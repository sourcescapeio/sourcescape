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

  val Servers = AnalysisType.all.map { at =>
    at -> configuration.get[String](at.server)
  }.toMap

  def runServerAnalysis(analysisType: AnalysisType, content: String): Future[Option[ByteString]] = {
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

  def startLanguageServer(analysisType: AnalysisType, indexId: Int, contents: Map[String, String]): Future[Unit] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    println("STARTING IN-MEMORY")
    contents.foreach {
      case (k, v) => {
        println(s"=====${k}======")
        println(v)
      }
    }
    for {
      response <- wsClient.url(url + "/language-server/" + indexId).post(contents)
      res = if (response.status =/= 200) {
        throw new Exception("Error starting language server")
      }
    } yield {
      ()
    }
  }

  def stopLanguageServer(analysisType: AnalysisType, indexId: Int): Future[Unit] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    println("STARTING LANGUAGE SERVER")
    for {
      response <- wsClient.url(url + "/language-server/" + indexId).delete()
      res = if (response.status =/= 200) {
        throw new Exception("Error stopping language server")
      }
    } yield {
      ()
    }
  }

  def languageServerRequest(analysisType: AnalysisType, indexId: Int, filename: String, location: Int): Future[JsValue] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    for {
      response <- wsClient.url(url + "/language-server/" + indexId + "/request").post(Json.obj(
        "filename" -> filename,
        "location" -> location))
      res = if (response.status =/= 200) {
        throw new Exception("Error stopping language server")
      }
    } yield {
      response.json
    }
  }

  def runAnalysis(analysisDirectory: String, analysisTree: AnalysisTree, content: ByteString): Future[Option[ByteString]] = {
    runServerAnalysis(analysisTree.analysisType, content.utf8String)
  }
}
