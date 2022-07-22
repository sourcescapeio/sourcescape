package models

import akka.util.ByteString
import akka.stream.scaladsl.{ Source, Flow, Sink }

object Sinks {
  val ByteAccum = Sink.fold(ByteString()) { (acc: ByteString, i: ByteString) =>
    acc ++ i
  }

  def ListAccum[T] = Sink.fold(List.empty[T]) { (acc: List[T], i: T) =>
    acc ++ List(i)
  }
}
