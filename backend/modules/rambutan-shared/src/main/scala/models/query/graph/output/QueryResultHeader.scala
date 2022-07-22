package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class QueryResultType(val identifier: String) extends Identifiable

object QueryResultType extends Plenumeration[QueryResultType] {
  // case object Node extends QueryResultType("node")
  case object GenericCode extends QueryResultType("generic_code")
  case object DocumentationAnnotation extends QueryResultType("documentation_annotation")

  //
  case object NodeTrace extends QueryResultType("node_trace")
  case object GraphTrace extends QueryResultType("graph_trace")
  case object NameTrace extends QueryResultType("name_trace")
  case object GenericGraphTrace extends QueryResultType("generic_graph_trace")

  case object Count extends QueryResultType("count")
  case object GroupingKey extends QueryResultType("grouping_key")
}

case class QueryColumnDefinition(
  name:    String,
  `type`:  QueryResultType,
  payload: JsValue         = Json.obj())

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
