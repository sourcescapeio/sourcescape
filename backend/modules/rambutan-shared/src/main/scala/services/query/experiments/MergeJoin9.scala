package services.q9

import silvousplay.imports._
import silvousplay.api._
import akka.stream.{ Attributes, FanInShape2, Inlet, Outlet }
import akka.stream.scaladsl.{ GraphDSL, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

import GraphDSL.Implicits._

import play.api.libs.json._

/**
 * Merge Join
 * - Assume left and right both come in sorted by T
 * - Built up from MergeSorted in akka/Graph.scala
 */
final class MergeJoin[T: Ordering, U1, U2](
  context:    SpanContext,
  doExplain:  Boolean,
  leftOuter:  Boolean,
  rightOuter: Boolean)(implicit val writes: Writes[T]) extends GraphStage[FanInShape2[(T, U1), (T, U2), (T, (Option[U1], Option[U2]))]] {
  private val left = Inlet[(T, U1)]("MergeJoin.left")
  private val right = Inlet[(T, U2)]("MergeJoin.right")
  private val out = Outlet[(T, (Option[U1], Option[U2]))]("MergeJoin.out")

  override val shape = new FanInShape2(left, right, out)

  def createLogic(attr: Attributes) = new GraphStageLogic(shape) {
    import Ordering.Implicits._
    setHandler(left, ignoreTerminateInput)
    setHandler(right, ignoreTerminateInput)
    setHandler(out, eagerTerminateOutput)

    /**
     * State
     */
    var currentLeftKey: T = _
    var currentLeftValues = collection.mutable.ListBuffer.empty[U1]

    var currentRightKey: T = _
    var currentRightValues = collection.mutable.ListBuffer.empty[U2]

    /**
     * Push heleprs
     */
    def incLeft(elem: (T, U1)) = {
      val (k, v) = elem
      if (currentLeftKey == null) {
        currentLeftKey = k
        currentLeftValues.append(v)
      } else if (currentLeftKey equiv k) {
        // add to set
        currentLeftValues.append(v)
      } else {
        currentLeftValues = collection.mutable.ListBuffer.empty[U1]
        currentLeftValues.append(v)
        currentLeftKey = k
      }
    }

    def incRight(elem: (T, U2)) = {
      val (k, v) = elem
      if (currentRightKey == null) {
        currentRightKey = k
        currentRightValues.append(v)
      } else if (currentRightKey equiv k) {
        // add to set
        currentRightValues.append(v)
      } else {
        currentRightValues = collection.mutable.ListBuffer.empty[U2]
        currentRightValues.append(v)
        currentRightKey = k
      }
    }

    def leftElementPush(leftElem: (T, U1)): Unit = {
      val (k, v) = leftElem
      // println("LEFT", leftElem)
      (currentLeftKey, currentRightKey) match {
        case (_, null) => {
          throw new Exception("improperly initialized right")
        }
        case (leftKey, _) if leftKey != null && k < leftKey => {
          throw new Exception("invalid sort on left side" + k + leftKey)
          // readR()
        }
        case (_, rightKey) if k > rightKey => {
          // ahead, read opposite side
          incLeft(leftElem)
          if (doExplain) {
            context.event("push.L", "key" -> ">", "push.key" -> k.toString(), "other.key" -> rightKey.toString())
          }
          readR()
        }
        case (_, rightKey) if k equiv rightKey => {
          val elems = calculateJoin(
            k,
            v :: Nil,
            currentRightValues.toList)
          incLeft(leftElem)
          if (doExplain) {
            context.event(
              "push.L",
              "key" -> "=",
              "push.key" -> k.toString(),
              "size" -> elems.size.toString())
          }
          emitMultiple(out, elems, readR)
        }
        case (_, rightKey) if k < rightKey => {
          // still behind, increment again
          incLeft(leftElem)
          if (doExplain) {
            context.event("push.L", "key" -> "<", "push.key" -> k.toString(), "other.key" -> rightKey.toString())
          }
          readL()
        }
      }
    }

    def rightElementPush(rightElem: (T, U2)): Unit = {
      val (k, v) = rightElem
      // println("RIGHT", rightElem)
      (currentLeftKey, currentRightKey) match {
        case (null, _) => {
          throw new Exception("improperly initialized left")
        }
        case (_, rightKey) if rightKey != null && k < rightKey => {
          throw new Exception("invalid sort on right side")
        }
        case (leftKey, _) if k > leftKey => {
          // ahead, read opposite side
          incRight(rightElem)
          if (doExplain) {
            context.event("push.R", "key" -> ">", "push.key" -> k.toString(), "other.key" -> leftKey.toString())
          }
          readL()
        }
        case (leftKey, _) if leftKey equiv k => {
          val elems = calculateJoin(
            k,
            currentLeftValues.toList,
            v :: Nil)
          incRight(rightElem)
          if (doExplain) {
            context.event(
              "push.R",
              "key" -> "=",
              "push.key" -> k.toString(),
              "other.key" -> leftKey.toString(),
              "size" -> elems.size.toString())
          }
          emitMultiple(out, elems, readL)
        }
        case (leftKey, _) if k < leftKey => {
          // still behind, increment again
          incRight(rightElem)
          if (doExplain) {
            context.event("push.R", "key" -> "<", "push.key" -> k.toString(), "other.key" -> leftKey.toString())
          }
          readR()
        }
      }
    }

    def calculateJoin(key: T, leftE: List[U1], rightE: List[U2]) = {
      // across List[V]
      for {
        le <- leftE
        re <- rightE
      } yield {
        (key, (Some(le), Some(re)))
      }
    }

    /**
     * Read triggers
     */
    val readR = () => {
      if (doExplain) {
        context.event("read.R")
      }
      read(right)(rightElementPush, passL)
    }
    val readL = () => {
      if (doExplain) {
        context.event("read.L")
      }
      read(left)(leftElementPush, passR)
    }

    /**
     * Helpers
     */
    // TODO: need to deal with flush out at end
    val passL = () => {
      if (doExplain) {
        context.event("pass.L")
      }
      passAlongMapConcat(left, out, doPull = true) { item =>
        withFlag(item._1 =?= currentRightKey) {
          calculateJoin(item._1, item._2 :: Nil, currentRightValues.toList)
        }
      }
    }

    val passR = () => {
      if (doExplain) {
        context.event("pass.R")
      }
      passAlongMapConcat(right, out, doPull = true) { item =>
        withFlag(item._1 =?= currentLeftKey) {
          calculateJoin(item._1, currentLeftValues.toList, item._2 :: Nil)
        }
      }
    }

    /**
     * Lib
     */
    private def passAlongMapConcat[In, Out](
      from:     Inlet[In],
      to:       Outlet[Out],
      doFinish: Boolean     = true,
      doFail:   Boolean     = true,
      doPull:   Boolean     = false)(f: In => List[Out]): Unit = {

      val ph = new InHandler with (() => Unit) {
        override def apply(): Unit = tryPull(from)

        override def onPush(): Unit = {
          val elem = grab(from)
          val outs = f(elem)
          emitMultiple(to, outs, this)
        }

        override def onUpstreamFinish(): Unit = if (doFinish) completeStage()

        override def onUpstreamFailure(ex: Throwable): Unit = if (doFail) failStage(ex)
      }

      // initialization check
      if (isAvailable(from)) {
        emitMultiple(to, f(grab(from)), ph)
      }
      if (doFinish && isClosed(from)) completeStage()

      setHandler(from, ph)

      if (doPull) tryPull(from)
    }

    /**
     * Initialization
     */
    override def preStart(): Unit = {
      // all fan-in stages need to eagerly pull all inputs to get cycles started
      pull(right)

      // initiate reads. first read left, then read right
      read(left)(l => {
        val (k, v) = l
        currentLeftKey = k
        currentLeftValues.append(v)
        readR()
      }, () => {
        passAlongMapConcat(right, out)(_ => Nil)
      })
    }
  }
}
