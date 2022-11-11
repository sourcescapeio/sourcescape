package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class QueryResultType(val identifier: String) extends Identifiable

object QueryResultType extends Plenumeration[QueryResultType] {
  // case object Node extends QueryResultType("node")
  // case object GenericCode extends QueryResultType("generic_code")

  // graph layer
  case object NodeTrace extends QueryResultType("node_trace")

  // relational layer. set at targeting level?
  case object GraphTrace extends QueryResultType("graph_trace")
  case object GenericGraphTrace extends QueryResultType("generic_graph_trace")

  // members
  case object String extends QueryResultType("string")
  case object Number extends QueryResultType("number")

  // traces
}

case class QueryColumnDefinition(
  name:   String,
  `type`: QueryResultType
// payload: JsValue         = Json.obj()
)

object QueryColumnDefinition {
  implicit val writes = Json.writes[QueryColumnDefinition]
}

case class QueryResultHeader(
  isDiff:  Boolean,
  columns: List[QueryColumnDefinition],
  // ordering:     List[String],
  sizeEstimate: Long)

object QueryResultHeader {
  implicit val writes = Json.writes[QueryResultHeader]
}
