package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.util.ByteString
import akka.stream.scaladsl.FileIO
import java.nio.file.{ Files, Paths }

@Singleton
class StaticAnalysisService @Inject() (
  wsClient:      WSClient,
  configuration: play.api.Configuration)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val Servers = AnalysisType.all.flatMap {
    case at: ServerAnalysisType => Option(at -> configuration.get[String](at.server))
    case _                      => None
  }.toMap

  def runServerAnalysis(analysisType: ServerAnalysisType, content: String): Future[Option[ByteString]] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    for {
      response <- wsClient.url(url + "/analyze").post(content)
      res <- if (response.status =/= 200) {
        println("Error getting analysis. Skipping")
        println(response.body)
        Future.successful(None)
      } else {
        response.bodyAsSource.runWith(Sinks.ByteAccum).map(Option.apply)
      }
    } yield {
      res
    }
  }

  def runAnalysis(analysisDirectory: String, analysisTree: AnalysisTree, content: ByteString): Future[Option[ByteString]] = {

    analysisTree.analysisType match {
      case at: ServerAnalysisType => {
        runServerAnalysis(at, content.utf8String)
      }
      case c: CompiledAnalysisType => {
        // read file
        val fileName = c.pathWithExtension {
          c.path(analysisDirectory, analysisTree.file)
        }

        val path = Paths.get(fileName)

        withFlag(Files.exists(path)) {
          FileIO.fromPath(Paths.get(fileName)).runWith(Sinks.ByteAccum).map(Option.apply)
        }
      }
    }
  }
}
