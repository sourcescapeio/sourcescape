package services

import scala.concurrent.{ ExecutionContext, Future }
import akka.util.ByteString
import akka.stream.scaladsl.{ Source, Sink }

trait FileService {

  val parallelism: Int
  implicit val ec: ExecutionContext
  implicit val mat: akka.stream.Materializer

  def writeFile(path: String, content: ByteString): Future[Unit]

  def readFile(path: String): Future[ByteString]

  // Used for compile
  def listDirectory(path: String): Source[(String, ByteString), Any]

  // Used for sweeper
  def deleteRecursively(path: String): Future[Unit]

  // utilities
  final def writeSource(source: Source[(String, ByteString), Any], path: String): Future[Unit] = {
    source.mapAsync(parallelism) {
      case (path, bytes) => writeFile(path, bytes)
    }.runWith(Sink.ignore) map (_ => ())
  }

}
