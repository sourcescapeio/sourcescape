package models.query

import play.api.libs.json._

// name:     Option[String], // for calculating unwind
// index:    Option[Int] // for calculating unwind

case class GenericGraphUnit(
  edgeType: Option[GraphEdgeType],
  orgId:    String,
  id:       String) {

  val key = orgId // alias for simplicity
}

object GenericGraphUnit {
  implicit val basic = new HasBasicExtraction[GenericGraphUnit] {
    // just gets basic id
    def getId(unit: GenericGraphUnit): String = {
      unit.id
    }

    // used to compute join keys for relational
    def getKey(unit: GenericGraphUnit): String = {
      s"${unit.orgId}/${unit.id}"
    }

    def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None): GenericGraphUnit = {
      val id = (js \ "_source" \ "id").as[String]
      val orgId = (js \ "_source" \ "org_id").as[String]

      GenericGraphUnit(
        edgeOverride,
        orgId,
        id)
    }
  }

  implicit val traceKey = new HasTraceKey[GenericGraphUnit] {
    def edgeTypeIdentifier(t: GenericGraphUnit): String = {
      t.edgeType.map(_.identifier).getOrElse("root")
    }

    def traceKey(t: GenericGraphUnit): TraceKey = {
      TraceKey(t.orgId, "", t.id)
    }
  }
}
