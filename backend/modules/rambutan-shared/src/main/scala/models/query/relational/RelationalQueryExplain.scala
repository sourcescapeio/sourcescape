package models.query

import silvousplay.imports._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, SourceQueueWithComplete }

sealed case class ExplainMessage(nodeKey: String, direction: String, data: JsValue)

object ExplainMessage {
  implicit val writes = Json.writes[ExplainMessage]
}

sealed case class RelationalQueryExplain(
  links:       collection.mutable.ListBuffer[(String, String)],
  keys:        collection.mutable.HashSet[String],
  source:      Option[Source[ExplainMessage, Any]],
  sourceQueue: Option[SourceQueueWithComplete[ExplainMessage]]) {

  def link(a: String, b: String) = {
    links.append((a, b))
  }

  private def edgeMap = {
    links.toList.groupBy(_._1).map {
      case (k, vs) => k -> vs.map(_._2)
    }
  }

  def headers = {
    Json.obj(
      "nodes" -> edgeMap.keys,
      "edges" -> edgeMap,
      "columns" -> keys.toList)
  }

  def pusher(key: String) = {
    keys += key

    withDefined(sourceQueue) { sq =>
      val f = { item: (String, JsObject) =>
        val (direction, data) = item
        val message = ExplainMessage(key, direction, data)
        sq.offer(message)
        ()
      }
      Option(f)
    }.getOrElse({ i: (String, JsObject) => () })
  }
}