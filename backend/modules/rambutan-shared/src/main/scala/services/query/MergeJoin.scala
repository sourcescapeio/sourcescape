package services

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
  rightOuter: Boolean
// v1SecondaryKey: U1 => T,
// v2SecondaryKey: U2 => T

)(implicit val writes: Writes[T]) extends GraphStage[FanInShape2[(T, U1), (T, U2), (T, (Option[U1], Option[U2]))]] {
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
    var currentRightKey: T = _

    var currentLeftValues = collection.mutable.ListBuffer.empty[U1]
    var currentRightValues = collection.mutable.ListBuffer.empty[U2]

    var currentLeftEmitted: Boolean = false
    var currentRightEmitted: Boolean = false

    /**
     * Lookahead state
     *
     * We need a lookahead because we cannot always guarantee correct order
     * Ex:
     * val source1 = ("1", "A"), ("2", "B")
     * val source2 = ("1", "AA"), ("1", "AAA")
     *
     * ("1", "AAA") is consumed after ("2", B")
     */
    var previousLeftKey: T = _
    var previousRightKey: T = _

    var previousLeftValues = collection.mutable.ListBuffer.empty[U1]
    var previousRightValues = collection.mutable.ListBuffer.empty[U2]

    var previousLeftEmitted: Boolean = false
    var previousRightEmitted: Boolean = false

    /**
     * Lookaheads
     * - See notice above
     */
    def checkPreviousRightElem(elem: (T, U1)) = {
      val (k, v) = elem
      withFlag(previousRightKey != null && (previousRightKey equiv k)) {
        calculateJoin(
          k,
          v :: Nil,
          previousRightValues.toList)
      }
    }

    def checkPreviousLeftElem(elem: (T, U2)) = {
      val (k, v) = elem
      withFlag(previousLeftKey != null && (previousLeftKey equiv k)) {
        calculateJoin(
          k,
          previousLeftValues.toList,
          v :: Nil)
      }
    }

    /**
     * Outer join flushes
     */
    def flushRightState(elemKey: T) = {
      withFlag(rightOuter) {
        // On rotation, previous falls out without having been matched
        lazy val shouldFlushPrev = elemKey > previousRightKey
        val flushPrev = withFlag(previousRightKey != null && shouldFlushPrev && !previousRightEmitted) {
          previousRightEmitted = true

          previousRightValues.toList.map { item =>
            (previousRightKey, (None, Some(item)))
          }
        }

        // we eagerly flush current if right side is ahead
        // otherwise, things get out of order
        lazy val shouldFlushCurr = elemKey > currentRightKey
        val flushCurr = withFlag(currentRightKey != null && shouldFlushCurr && !currentRightEmitted) {
          currentRightEmitted = true

          currentRightValues.toList.map { item =>
            (currentRightKey, (None, Some(item)))
          }
        }

        flushPrev ++ flushCurr
      }
    }

    def flushLeftState(elemKey: T) = {
      withFlag(leftOuter) {
        // println("flush.L", elemKey, previousLeftKey, currentLeftKey)
        // On rotation, previous falls out without having been matched
        lazy val shouldFlushPrev = elemKey > previousLeftKey
        val flushPrev = withFlag(previousLeftKey != null && shouldFlushPrev && !previousLeftEmitted) {
          previousLeftEmitted = true

          previousLeftValues.toList.map { item =>
            (previousLeftKey, (Some(item), None))
          }
        }

        // we eagerly flush current if right side is ahead
        // otherwise, things get out of order
        lazy val shouldFlushCurr = elemKey > currentLeftKey
        val flushCurr = withFlag(currentLeftKey != null && shouldFlushCurr && !currentLeftEmitted) {
          currentLeftEmitted = true

          currentLeftValues.toList.map { item =>
            (currentLeftKey, (Some(item), None))
          }
        }

        flushPrev ++ flushCurr
      }
    }

    /**
     * State increment
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
        // actual inc
        previousLeftEmitted = currentLeftEmitted
        previousLeftValues = currentLeftValues
        previousLeftKey = currentLeftKey

        currentLeftEmitted = false
        currentLeftValues = collection.mutable.ListBuffer.empty[U1]
        currentLeftValues.append(v)
        currentLeftKey = k

        // println("left.changed", previousLeftKey, currentLeftKey)
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
        previousRightEmitted = currentRightEmitted
        previousRightValues = currentRightValues
        previousRightKey = currentRightKey

        currentRightEmitted = false
        currentRightValues = collection.mutable.ListBuffer.empty[U2]
        currentRightValues.append(v)
        currentRightKey = k

        // println("right.changed", previousRightKey, currentRightKey)
      }
    }

    /**
     * Element push handlers
     */
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
          if (doExplain) {
            context.event("push.L", "key" -> ">", "push.key" -> k.toString(), "other.key" -> rightKey.toString())
          }

          // ahead, read opposite side
          val emit = flushRightState(k)
          incLeft(leftElem)

          emitMultiple(out, emit, readR)
        }
        case (_, rightKey) if k equiv rightKey => {
          val elems = calculateJoin(
            k,
            v :: Nil,
            currentRightValues.toList)

          if (doExplain) {
            context.event(
              "push.L",
              "key" -> "=",
              "push.key" -> k.toString(),
              "size" -> elems.size.toString())
          }

          val emit = flushRightState(k)
          incLeft(leftElem)
          currentLeftEmitted = true
          currentRightEmitted = true

          // emit is always before because always < k
          emitMultiple(out, emit ++ elems, readR)
        }
        case (_, rightKey) if k < rightKey => {
          if (doExplain) {
            context.event("push.L", "key" -> "<", "push.key" -> k.toString(), "other.key" -> rightKey.toString())
          }

          // must do this before incLeft
          val lookaheadEmit = checkPreviousRightElem(leftElem)
          // val emit = flushLeftState(k)

          // still behind, increment again
          incLeft(leftElem)

          // if we did not emit
          val emit = withFlag(leftOuter && lookaheadEmit.length =?= 0) {
            currentLeftEmitted = true
            (k, (Some(v), None)) :: Nil
          }

          emitMultiple(out, emit ++ lookaheadEmit, readL)
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
          if (doExplain) {
            context.event("push.R", "key" -> ">", "push.key" -> k.toString(), "other.key" -> leftKey.toString())
          }
          // ahead, read opposite side
          val emit = flushLeftState(k)
          incRight(rightElem)

          emitMultiple(out, emit, readL)
        }
        case (leftKey, _) if leftKey equiv k => {
          val elems = calculateJoin(
            k,
            currentLeftValues.toList,
            v :: Nil)
          if (doExplain) {
            context.event(
              "push.R",
              "key" -> "=",
              "push.key" -> k.toString(),
              "other.key" -> leftKey.toString(),
              "size" -> elems.size.toString())
          }

          val emit = flushLeftState(k)
          incRight(rightElem)
          currentLeftEmitted = true
          currentRightEmitted = true

          // emit is always before because always < k
          emitMultiple(out, emit ++ elems, readL)
        }
        case (leftKey, _) if k < leftKey => {
          if (doExplain) {
            context.event("push.R", "key" -> "<", "push.key" -> k.toString(), "other.key" -> leftKey.toString())
          }

          // must do this before incRight
          val lookaheadEmit = checkPreviousLeftElem(rightElem)
          // still behind, increment again
          incRight(rightElem)

          val emit = withFlag(rightOuter && lookaheadEmit.length =?= 0) {
            currentRightEmitted = true
            (k, (None, Some(v))) :: Nil
          }

          // how do we sort these??
          emitMultiple(out, emit ++ lookaheadEmit, readR)
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
    def flushTerminal() = {
      val leftFlush = withFlag(leftOuter) {
        val prevEmit = withFlag(!previousLeftEmitted) {
          previousLeftValues.toList.map { item =>
            (previousLeftKey, (Some(item), None))
          }
        }
        val currEmit = withFlag(!currentLeftEmitted) {
          currentLeftValues.toList.map { item =>
            (currentLeftKey, (Some(item), None))
          }
        }

        prevEmit ++ currEmit
      }

      val rightFlush = withFlag(rightOuter) {
        val prevEmit = withFlag(!previousRightEmitted) {
          previousRightValues.toList.map { item =>
            (previousRightKey, (None, Some(item)))
          }
        }

        val currEmit = withFlag(!currentRightEmitted) {
          currentRightValues.toList.map { item =>
            (currentRightKey, (None, Some(item)))
          }
        }

        prevEmit ++ currEmit
      }

      (leftFlush ++ rightFlush).sortBy(_._1)
    }

    val passL = () => {
      val toFlush = flushTerminal()

      if (doExplain) {
        context.event("pass.L", "flushed" -> toFlush.size.toString())
      }

      emitMultiple(out, toFlush, () => {
        passAlongMapConcat(left, out, doPull = true) { item =>
          if (item._1 =?= currentRightKey) {
            calculateJoin(item._1, item._2 :: Nil, currentRightValues.toList)
          } else {
            withFlag(leftOuter) {
              (item._1, (Some(item._2), None)) :: Nil
            }
          }
        }
      })
    }

    val passR = () => {
      val toFlush = flushTerminal()

      if (doExplain) {
        context.event("pass.R", "flushed" -> toFlush.size.toString())
      }

      emitMultiple(out, toFlush, () => {
        passAlongMapConcat(right, out, doPull = true) { item =>
          if (item._1 =?= currentLeftKey) {
            calculateJoin(item._1, currentLeftValues.toList, item._2 :: Nil)
          } else {
            // outer joins we must
            withFlag(rightOuter) {
              (item._1, (None, Some(item._2))) :: Nil
            }
          }
        }
      })
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
        if (doExplain) {
          context.event("initial.L")
        }
        currentLeftKey = k
        currentLeftValues.append(v)
        readR()
      }, () => {
        passAlongMapConcat(right, out)(_ => Nil)
      })
    }
  }
}
