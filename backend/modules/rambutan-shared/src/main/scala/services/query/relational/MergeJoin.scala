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
 */
final class MergeJoin[T: Ordering, U1, U2](
  pushExplain: ((String, JsObject)) => Unit,
  context:     SpanContext,
  doExplain:   Boolean,
  leftOuter:   Boolean,
  rightOuter:  Boolean)(implicit val writes: Writes[T]) extends GraphStage[FanInShape2[(T, U1), (T, U2), (T, (Option[U1], Option[U2]))]] {
  private val left = Inlet[(T, U1)]("MergeJoin.left")
  private val right = Inlet[(T, U2)]("MergeJoin.right")
  private val out = Outlet[(T, (Option[U1], Option[U2]))]("MergeJoin.out")

  override val shape = new FanInShape2(left, right, out)

  override def createLogic(attr: Attributes) = new GraphStageLogic(shape) {
    import Ordering.Implicits._
    setHandler(left, ignoreTerminateInput)
    setHandler(right, ignoreTerminateInput)
    setHandler(out, eagerTerminateOutput)

    // for lookahead joining
    var previousLeftKey: T = _
    var previousRightKey: T = _

    var previousLeftSet = collection.mutable.ListBuffer.empty[U1]
    var previousRightSet = collection.mutable.ListBuffer.empty[U2]

    //
    var currentLeftKey: T = _
    var currentRightKey: T = _

    var currentLeftSet = collection.mutable.ListBuffer.empty[U1]
    var currentRightSet = collection.mutable.ListBuffer.empty[U2]

    // for left joins
    var previousLeftMatched: Boolean = false
    var currentLeftMatched: Boolean = false

    // for right joins
    var previousRightMatched: Boolean = false
    var currentRightMatched: Boolean = false

    // calculates cross product
    def calculateJoin(key: T, leftE: List[U1], rightE: List[U2]) = {
      // across List[V]
      for {
        le <- leftE
        re <- rightE
      } yield {
        (key, (Some(le), Some(re)))
      }
    }

    def flushLeftState(elemKey: T) = {
      withFlag(leftOuter) {
        // emit previous
        val flushPrev = withFlag(previousLeftKey != null && !previousLeftMatched) {
          previousLeftSet.toList.map { item =>
            pushExplain(("outerjoin", Json.obj(
              "flushed" -> "left",
              "key" -> previousLeftKey)))
            (previousLeftKey, (Some(item), None))
          }
        }

        // we also need to flush current if right side is ahead
        // otherwise, things get out of order
        val shouldFlushCurr = elemKey >= currentRightKey
        val flushCurr = withFlag(currentLeftKey != null && shouldFlushCurr && !currentLeftMatched) {
          currentLeftMatched = true

          currentLeftSet.toList.map { item =>
            pushExplain(("outerjoin", Json.obj(
              "flushed" -> "leftc",
              "key" -> currentLeftKey)))
            (currentLeftKey, (Some(item), None))
          }
        }

        flushPrev ++ flushCurr
      }
    }

    def flushRightState(elemKey: T) = {
      withFlag(rightOuter) {
        // emit previous
        val flushPrev = withFlag(previousRightKey != null && !previousRightMatched) {
          previousRightSet.toList.map { item =>
            pushExplain(("outerjoin", Json.obj(
              "flushed" -> "right",
              "key" -> previousRightKey)))
            (previousRightKey, (None, Some(item)))
          }
        }

        // we also need to flush current if right side is ahead
        // otherwise, things get out of order
        val shouldFlushCurr = elemKey >= currentLeftKey
        val flushCurr = withFlag(currentRightKey != null && shouldFlushCurr && !currentRightMatched) {
          currentRightMatched = true

          currentRightSet.toList.map { item =>
            pushExplain(("outerjoin", Json.obj(
              "flushed" -> "rightc",
              "key" -> currentRightKey)))
            (currentRightKey, (None, Some(item)))
          }
        }

        flushPrev ++ flushCurr
      }
    }

    def incLeft(elem: (T, U1)): List[(T, (Option[U1], Option[U2]))] = {
      val (k, v) = elem
      if (currentLeftKey == null) {
        currentLeftKey = k
        currentLeftSet.append(v)
        Nil // emit Nil
      } else if (currentLeftKey equiv k) {
        // add to set
        currentLeftSet.append(v)
        Nil // emit Nil
      } else {
        // flush for left join
        val toEmit = flushLeftState(k)
        // reset
        previousLeftMatched = currentLeftMatched
        previousLeftSet = currentLeftSet
        previousLeftKey = currentLeftKey
        currentLeftMatched = false
        currentLeftSet = collection.mutable.ListBuffer.empty[U1]
        currentLeftSet.append(v)
        currentLeftKey = k
        toEmit
      }
    }

    def incRight(elem: (T, U2)): List[(T, (Option[U1], Option[U2]))] = {
      val (k, v) = elem
      if (currentRightKey == null) {
        currentRightKey = k
        currentRightSet.append(v)
        Nil
      } else if (currentRightKey equiv k) {
        // add to set
        currentRightSet.append(v)
        Nil
      } else {
        //flush for right join
        val toEmit = flushRightState(k)
        // reset
        previousRightMatched = currentRightMatched
        previousRightSet = currentRightSet
        previousRightKey = currentRightKey
        currentRightMatched = false
        currentRightSet = collection.mutable.ListBuffer.empty[U2]
        currentRightSet.append(v)
        currentRightKey = k
        toEmit
      }
    }

    /**
     * Lookaheads
     * - Need to check previous item for potential join
     * - Example: List((3, "three"), (4, "four")) join List((3, "3ree"), (3, "3"))
     * - We will first join the first two elements, then inc to (4, "four") on the left side
     * - We need to retain (3, "three") on the left side so that it can be joined to (3, "3") later
     */
    def checkPreviousRightElem(elem: (T, U1)) = {
      val (k, v) = elem
      if (previousRightKey != null && (previousRightKey equiv k)) {
        val elems = calculateJoin(
          k,
          v :: Nil,
          previousRightSet.toList)
        previousRightMatched = true
        currentLeftMatched = true
        emitMultiple(out, elems, readL)
      } else {
        // next
        readL()
      }
    }

    def checkPreviousLeftElem(elem: (T, U2)) = {
      val (k, v) = elem
      if (previousLeftKey != null && (previousLeftKey equiv k)) {
        val elems = calculateJoin(
          k,
          previousLeftSet.toList,
          v :: Nil)
        currentRightMatched = true
        previousLeftMatched = true
        emitMultiple(out, elems, readR)
      } else {
        readR()
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
          // readL()
        }
        case (leftKey, _) if k > leftKey => {
          // ahead, read opposite side
          val rightEmit = incRight(rightElem)
          if (doExplain) {
            context.event("push.R", "key" -> ">", "push.key" -> k.toString(), "size" -> rightEmit.size.toString())
          }
          emitMultiple(out, rightEmit, readL)
        }
        case (leftKey, _) if leftKey equiv k => {
          val elems = calculateJoin(
            k,
            currentLeftSet.toList,
            v :: Nil)
          pushExplain(("leftjoin", Json.obj(
            "join" -> "right",
            "key" -> k)))
          val rightEmit = incRight(rightElem)
          currentLeftMatched = true
          currentRightMatched = true // must be after
          if (doExplain) {
            context.event(
              "push.R",
              "key" -> "=",
              "push.key" -> k.toString(),
              "size" -> (rightEmit.size + elems.size).toString(),
              "size.elems" -> elems.size.toString())
          }
          emitMultiple(out, rightEmit ++ elems, readL)
        }
        case (leftKey, _) if k < leftKey => {
          // still behind, increment again
          val rightEmit = incRight(rightElem)
          if (doExplain) {
            context.event("push.R", "key" -> "<", "push.key" -> k.toString(), "size" -> rightEmit.size.toString())
          }
          emitMultiple(out, rightEmit, () => {
            checkPreviousLeftElem(rightElem)
          })
        }
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
          val leftEmit = incLeft(leftElem)
          if (doExplain) {
            context.event("push.L", "key" -> ">", "push.key" -> k.toString(), "size" -> leftEmit.size.toString())
          }
          emitMultiple(out, leftEmit, readR)
        }
        case (_, rightKey) if k equiv rightKey => {
          val elems = calculateJoin(
            k,
            v :: Nil,
            currentRightSet.toList)
          pushExplain(("leftjoin", Json.obj(
            "join" -> "left",
            "key" -> k)))
          val leftEmit = incLeft(leftElem)
          currentRightMatched = true
          currentLeftMatched = true // must be after incLeft
          if (doExplain) {
            context.event(
              "push.L",
              "key" -> "=",
              "push.key" -> k.toString(),
              "size" -> (leftEmit.size + elems.size).toString(),
              "size.elems" -> elems.size.toString())
          }
          emitMultiple(out, leftEmit ++ elems, readR)
        }
        case (_, rightKey) if k < rightKey => {
          // still behind, increment again
          val leftEmit = incLeft(leftElem)
          if (doExplain) {
            context.event("push.L", "key" -> "<", "push.key" -> k.toString(), "size" -> leftEmit.size.toString())
          }
          emitMultiple(out, leftEmit, () => {
            checkPreviousRightElem(leftElem)
          })
          // will readL()
        }
      }
    }

    val dispatchR = rightElementPush _
    val dispatchL = leftElementPush _

    // this is the final step. null everything out

    def flushTerminal() = {
      val leftFlush = withFlag(leftOuter) {
        val prevEmit = withFlag(!previousLeftMatched) {
          previousLeftSet.toList.map { item =>
            (previousLeftKey, (Some(item), None))
          }
        }
        val currEmit = withFlag(!currentLeftMatched) {
          currentLeftSet.toList.map { item =>
            (currentLeftKey, (Some(item), None))
          }
        }

        prevEmit ++ currEmit
      }

      val rightFlush = withFlag(rightOuter) {
        val prevEmit = withFlag(!previousRightMatched) {
          previousRightSet.toList.map { item =>
            (previousRightKey, (None, Some(item)))
          }
        }

        val currEmit = withFlag(!currentRightMatched) {
          currentRightSet.toList.map { item =>
            (currentRightKey, (None, Some(item)))
          }
        }

        prevEmit ++ currEmit
      }

      (leftFlush ++ rightFlush).sortBy(_._1)
    }

    val passR = () => {
      if (doExplain) {
        context.event("pass.R")
      }
      val toFlush = flushTerminal()

      emitMultiple(out, toFlush, () => {
        passAlongMapConcat(right, out, doPull = true) { item =>
          // handle existing joins
          if (item._1 =?= currentLeftKey) {
            calculateJoin(item._1, currentLeftSet.toList, item._2 :: Nil)
          } else {
            withFlag(rightOuter) {
              (item._1, (None, Some(item._2))) :: Nil
            }
          }
        }
      })
    }
    val passL = () => {
      if (doExplain) {
        context.event("pass.L")
      }
      val toFlush = flushTerminal()

      emitMultiple(out, toFlush, () => {
        passAlongMapConcat(left, out, doPull = true) { item =>
          // handle existing joins
          if (item._1 =?= currentRightKey) {
            calculateJoin(item._1, item._2 :: Nil, currentRightSet.toList)
          } else {
            withFlag(leftOuter) {
              (item._1, (Some(item._2), None)) :: Nil
            }
          }
        }
      })
    }

    val readR = () => {
      if (doExplain) {
        context.event("read.R")
      }
      read(right)(dispatchR, passL)
    }
    val readL = () => {
      if (doExplain) {
        context.event("read.L")
      }
      read(left)(dispatchL, passR)
    }

    override def preStart(): Unit = {
      // all fan-in stages need to eagerly pull all inputs to get cycles started
      pull(right)

      // initiate reads. first read left, then read right
      read(left)(l => {
        val (k, v) = l
        currentLeftKey = k
        currentLeftSet.append(v)
        readR()
      }, () => {
        // throw away everything if no left
        if (rightOuter) {
          passAlongMapConcat(right, out) { item =>
            (item._1, (None, Some(item._2))) :: Nil
          }
        } else {
          passAlongMapConcat(right, out)(_ => Nil)
        }
      })
    }

    /**
     * Helpers
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
  }
}

/**
 * GraphStage primitives reference
 */

// .setHandler

/**
 * Requests an element on the given port. Calling this method twice before an element arrived will fail.
 * There can only be one outstanding request at any given time. The method [[hasBeenPulled]] can be used
 * query whether pull is allowed to be called or not. This method will also fail if the port is already closed.
 */
//.pull(in)

/**
 * Read an element from the given inlet and continue with the given function,
 * suspending execution if necessary. This action replaces the [[InHandler]]
 * for the given inlet if suspension is needed and reinstalls the current
 * handler upon receiving the `onPush()` signal (before invoking the `andThen` function).
 */
// .read(inlet)(andThen, onClose)

/**
 * Install a handler on the given inlet that emits received elements on the
 * given outlet before pulling for more data. `doFinish` and `doFail` control whether
 * completion or failure of the given inlet shall lead to operator termination or not.
 * `doPull` instructs to perform one initial pull on the `from` port.
 */
// .passAlong(from, to, doFinish, doFail, doPull)

/**
 * Emit an element through the given outlet and continue with the given thunk
 * afterwards, suspending execution if necessary.
 * This action replaces the [[OutHandler]] for the given outlet if suspension
 * is needed and reinstalls the current handler upon receiving an `onPull()`
 * signal (before invoking the `andThen` function).
 */
// .emit(out, elem, andThen)
