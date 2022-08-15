package services

import akka.stream.{ Attributes, FanOutShape2, Inlet, Outlet }
import akka.stream.scaladsl.{ GraphDSL, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

import GraphDSL.Implicits._

import play.api.libs.json._

/**
 * Need to use this buffer to avoid deadlocks
 */

final class JoinBuffer[A, B](val pushExplain: ((String, JsObject)) => Unit) extends GraphStage[FanOutShape2[Either[Either[A, Unit], Either[B, Unit]], List[A], List[B]]] {

  type InputShape = Either[Either[A, Unit], Either[B, Unit]]

  val in: Inlet[InputShape] = Inlet[InputShape]("JoinBuffer.in")

  val out1: Outlet[List[A]] = Outlet[List[A]]("JoinBuffer.out1")
  val out2: Outlet[List[B]] = Outlet[List[B]]("JoinBuffer.out2")

  override val shape: FanOutShape2[InputShape, List[A], List[B]] = {
    new FanOutShape2(in, out1, out2)
  }

  val MAX_BUFFER = 1000

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var leftBuffer = collection.mutable.ListBuffer.empty[A]
    var rightBuffer = collection.mutable.ListBuffer.empty[B]

    var leftHasCompleted = false
    var rightHasCompleted = false

    var shouldCompleteLeft = false
    var shouldCompleteRight = false

    private def rotateLeft() = {
      val flushLeft = leftBuffer.toList
      if (leftBuffer.length > MAX_BUFFER) {
        pushExplain(("overflow", Json.obj("dir" -> "left", "size" -> leftBuffer.length)))
      }
      leftBuffer = collection.mutable.ListBuffer.empty[A]
      flushLeft
    }

    private def rotateRight() = {
      val flushRight = rightBuffer.toList
      if (rightBuffer.length > MAX_BUFFER) {
        pushExplain(("overflow", Json.obj("dir" -> "right", "size" -> rightBuffer.length)))
      }
      rightBuffer = collection.mutable.ListBuffer.empty[B]
      flushRight
    }

    setHandler(in, new InHandler {
      override def onUpstreamFinish(): Unit = {
        pushExplain(("buffer", Json.obj("action" -> "upstream-finish")))
      }

      override def onPush(): Unit = {
        grab(in) match {
          case Left(Left(a)) => {
            if (isAvailable(out1)) {
              val flushLeft = rotateLeft()
              pushExplain(("buffer", Json.obj("action" -> "push-left", "size" -> (flushLeft.size + 1))))
              push(out1, flushLeft :+ a)
            } else {
              pushExplain(("buffer", Json.obj("action" -> "queue-left", "key" -> a.asInstanceOf[(List[String], Any)]._1)))
              leftBuffer.append(a)
            }

            if (isAvailable(out2)) {
              val flushRight = rotateRight()
              if (flushRight.length > 0) {
                pushExplain(("buffer", Json.obj("action" -> "flush-right")))
              }
              push(out2, flushRight)
            }
          }
          case Right(Left(a)) => {
            // pushExplain(("right-buffer", Json.obj("key" -> a.asInstanceOf[(List[String], Any)]._1)))
            if (isAvailable(out1)) {
              val flushLeft = rotateLeft()
              push(out1, flushLeft)
            }

            if (isAvailable(out2)) {
              val flushRight = rotateRight()
              pushExplain(("buffer", Json.obj("action" -> "push-right", "size" -> (flushRight.size + 1))))
              push(out2, flushRight :+ a)
            } else {
              pushExplain(("buffer", Json.obj("action" -> "queue-right")))
              rightBuffer.append(a)
            }
          }
          // LEOF
          case Left(Right(_)) => {
            pushExplain(("buffer", Json.obj("action" -> "left-eof")))
            if (isAvailable(out1)) {
              val flushLeft = rotateLeft()
              push(out1, flushLeft)
            }
            if (isAvailable(out2)) {
              val flushRight = rotateRight()
              push(out2, flushRight)
            }
            shouldCompleteLeft = true
          }
          // REOF
          case Right(Right(_)) => {
            pushExplain(("buffer", Json.obj("action" -> "right-eof")))
            if (isAvailable(out1)) {
              val flushLeft = rotateLeft()
              push(out1, flushLeft)
            }
            if (isAvailable(out2)) {
              val flushRight = rotateRight()
              push(out2, flushRight)
            }
            shouldCompleteRight = true
          }
        }
      }
    })

    setHandler(out1, new OutHandler {
      override def onPull(): Unit = {
        pushExplain(("buffer", Json.obj("action" -> "pull-left")))

        if (shouldCompleteLeft) {
          pushExplain(("buffer", Json.obj("action" -> "complete-left")))
          val flushLeft = rotateLeft()
          leftHasCompleted = true
          emit(out1, flushLeft)
          complete(out1)
          if (rightHasCompleted) {
            completeStage()
          }
        } else {
          if (!hasBeenPulled(in)) {
            pull(in)
          }
        }
      }
    })

    setHandler(out2, new OutHandler {
      override def onPull(): Unit = {
        pushExplain(("buffer", Json.obj("action" -> "pull-right")))

        if (shouldCompleteRight) {
          pushExplain(("buffer", Json.obj("action" -> "complete-right")))
          val flushRight = rotateRight()
          emit(out2, flushRight)
          rightHasCompleted = true
          complete(out2)
          if (leftHasCompleted) {
            completeStage()
          }
        } else {
          if (!hasBeenPulled(in)) {
            pull(in)
          }
        }
      }
    })
  }
}
