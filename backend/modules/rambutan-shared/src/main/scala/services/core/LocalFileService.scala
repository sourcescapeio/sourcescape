package services

import models._

import silvousplay.imports._

import scala.concurrent.{ ExecutionContext, Future }
import javax.inject._

import org.apache.commons.io.FileUtils
import java.io.File
import akka.util.ByteString
import akka.stream.scaladsl.{ Source, FileIO, Sink }
import java.nio.file.Paths
import scala.jdk.CollectionConverters._

@Singleton
class LocalFileService @Inject() (
  configuration: play.api.Configuration)(implicit val ec: ExecutionContext, val mat: akka.stream.Materializer) extends FileService {

  val WorkingDirectory = configuration.get[String]("working.directory")

  val parallelism: Int = 1

  /**
   * Core impls. Also used to directly access local file systems
   */
  private def ensureFile(file: String) = Future {
    FileUtils.forceMkdirParent(new File(file))
  }

  def writeAbsolute(absolutePath: String, content: ByteString): Future[Unit] = {
    for {
      _ <- ensureFile(absolutePath)
      _ <- Source(Seq(content)).runWith {
        FileIO.toPath(Paths.get(absolutePath))
      }
    } yield {
      ()
    }
  }

  def readAbsolute(absolutePath: String): Future[ByteString] = {
    FileIO.fromPath(Paths.get(absolutePath)).runWith(Sinks.ByteAccum)
  }

  def listAbsolute(absolutePath: String): Source[(String, ByteString), Any] = {
    val fileListing = FileUtils.listFiles(new File(absolutePath), null, true)

    Source(fileListing.asScala.toList).mapAsync(2) { file =>
      val relativePath = file.getAbsolutePath().replaceFirst(s"^${absolutePath}", "")
      Future {
        val bytes = ByteString(FileUtils.readFileToByteArray(file))
        (relativePath, bytes)
      }
    }
  }

  def deleteRecursiveAbsolute(absolutePath: String): Future[Unit] = Future {
    new scala.reflect.io.Directory(new java.io.File(absolutePath)).deleteRecursively()
  }

  /**
   * FileService overrides
   */
  private def toPath(path: String) = s"${WorkingDirectory}/${path}"

  override def writeFile(path: String, content: ByteString): Future[Unit] = {
    writeAbsolute(toPath(path), content)
  }

  override def readFile(path: String): Future[ByteString] = {
    readAbsolute(toPath(path))
  }

  override def listDirectory(path: String): Source[(String, ByteString), Any] = {
    listAbsolute(toPath(path))
  }

  override def deleteRecursively(path: String): Future[Unit] = {
    deleteRecursiveAbsolute(toPath(path))
  }
}
