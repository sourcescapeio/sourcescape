package models.query

import play.api.libs.json._

case class TraceUnit(
  edgeType: Option[GraphEdgeType], // for tracking previous hop, also for calculating unwind
  key:      String,
  path:     String,
  id:       String,
  name:     Option[String], // for calculating unwind
  index:    Option[Int] // for calculating unwind
)

object TraceUnit {
  implicit val basic = new HasBasicExtraction[TraceUnit] {
    def getId(unit: TraceUnit) = unit.id

    def getKey(unit: TraceUnit) = {
      s"${unit.key}/${unit.path}/${unit.id}"
    }

    def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
      val key = (js \ "_source" \ "key").as[String]
      val path = (js \ "_source" \ "path").as[String]
      val id = (js \ "_source" \ "id").as[String]
      val name = (js \ "_source" \ "name").asOpt[String]
      val index = (js \ "_source" \ "index").asOpt[Int]

      TraceUnit(edgeOverride, key, path, id, name, index)
    }
  }

  implicit val traceKey = new HasTraceKey[TraceUnit] {
    def edgeTypeIdentifier(t: TraceUnit): String = {
      t.edgeType.map(_.identifier).getOrElse("root")
    }

    def traceKey(t: TraceUnit): TraceKey = {
      TraceKey(t.key, t.path, t.id)
    }
  }
}