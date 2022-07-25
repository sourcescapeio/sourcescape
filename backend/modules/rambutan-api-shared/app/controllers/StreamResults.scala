package controllers

import scala.concurrent.duration._
import akka.stream.scaladsl.Source
import play.api.libs.json._
import akka.util.ByteString

trait StreamResults {
  private val KeepAliveObj = Json.obj("ignore" -> true)

  protected def streamQuery[T, S](tableHeader: T, source: Source[S, Any])(implicit w1: Writes[T], w2: Writes[S]) = {
    val recoveredSource = source.map { i: S =>
      Json.toJson(i)
    }.recover {
      case e: silvousplay.api.APIException => {
        Json.obj(
          "type" -> "error",
          "obj" -> e.dto,
          "error" -> e.dto // back compat
        )
      }
    }

    val initial = Source(List(KeepAliveObj))
    val byteSerialized = initial.concat(recoveredSource.keepAlive(1.second, () => KeepAliveObj)).map(Json.stringify).intersperse("", "\n", "\n").map(ByteString(_))

    play.api.mvc.Result(
      header = play.api.mvc.ResponseHeader(200, Map(
        "Rambutan-Result" -> Json.stringify(
          Json.toJson(tableHeader)))),
      body = play.api.http.HttpEntity.Chunked(
        byteSerialized.mapConcat {
          case c if c.length > 0 => play.api.http.HttpChunk.Chunk(c) :: Nil
          case _                 => Nil
        },
        Some("application/json-seq")))
  }
}