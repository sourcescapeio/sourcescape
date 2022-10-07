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

case class SymbolLookup(
  file: String,
  startIndex: Int,
  endIndex: Int,
  // debug
  kind: String,
  name: String,
  containerName: String
) {
  def key = s"${file}:${startIndex}:${endIndex}"
}

object SymbolLookup {

  implicit val writes = Json.writes[SymbolLookup]

  def parse(d: JsValue) = {
    val start = (d \ "contextSpan" \ "start").as[Int]
    val length = (d \ "contextSpan" \ "length").as[Int]
    SymbolLookup(
      (d \ "fileName").as[String],
      start,
      start + length,
      (d \ "kind").as[String],
      (d \ "name").as[String],
      (d \ "containerName").as[String],
    )
  }
}

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

  def startDirectoryLanguageServer(analysisType: AnalysisType, indexId: Int, directories: List[String]): Future[Unit] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    println("STARTING DIRECTORY LANGUAGE SERVER", directories)
    for {
      response <- wsClient.url(url + "/language-server/" + indexId + "/directory").post(Json.obj(
        "directories" -> directories
      ))
      res = if (response.status =/= 200) {
        throw new Exception("Error starting language server")
      }
    } yield {
      ()
    }
  }  

  def startInMemoryLanguageServer(analysisType: AnalysisType, indexId: Int, contents: Map[String, String]): Future[Unit] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    println("STARTING IN-MEMORY LANGUAGE SERVER")
    contents.foreach {
      case (k, v) => {
        println(s"=====${k}======")
        println(v)
      }
    }
    for {
      response <- wsClient.url(url + "/language-server/" + indexId + "/memory").post(contents)
      res = if (response.status =/= 200) {
        println(response.body)
        throw new Exception("Error starting language server")
      }
    } yield {
      println(response.body)
      ()
    }
  }

  def stopLanguageServer(analysisType: AnalysisType, indexId: Int): Future[Unit] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    println("STOPPING LANGUAGE SERVER", indexId)
    for {
      response <- wsClient.url(url + "/language-server/" + indexId).delete()
      res = if (response.status =/= 200) {
        throw new Exception("Error stopping language server")
      }
    } yield {
      ()
    }
  }

  def languageServerRequest(analysisType: AnalysisType, indexId: Int, filename: String, location: Int): Future[(List[SymbolLookup], List[SymbolLookup], JsValue)] = {
    val url = Servers.getOrElse(analysisType, throw new Exception("invalid server analysis type"))
    for {
      response <- wsClient.url(url + "/language-server/" + indexId + "/request").post(Json.obj(
        "filename" -> filename,
        "location" -> location))
      res = if (response.status =/= 200) {
        throw new Exception("Error stopping language server")
      }
    } yield {
      val json = response.json \ "response"

      (
        (json \ "definition").as[List[JsValue]].map(SymbolLookup.parse),
        (json \ "typeDefinition").as[List[JsValue]].map(SymbolLookup.parse),
        json.as[JsValue]
      )
    }
  }

  def runAnalysis(analysisDirectory: String, analysisTree: AnalysisTree, content: ByteString): Future[Option[ByteString]] = {
    runServerAnalysis(analysisTree.analysisType, content.utf8String)
  }
}
