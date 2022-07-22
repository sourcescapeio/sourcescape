package models

import play.api.libs.json._

case class QueryCache(
  id:    Int,
  orgId: Int,
  // query
  queryId:   Int,
  forceRoot: String,
  ordering:  List[String],
  // targeting
  fileFilter: Option[String]) {

  def dto = QueryCacheDTO(id, queryId, forceRoot, ordering, fileFilter)
}

case class QueryCacheDTO(
  id:         Int,
  queryId:    Int,
  forceRoot:  String,
  ordering:   List[String],
  fileFilter: Option[String])

object QueryCacheDTO {
  implicit val writes = Json.writes[QueryCacheDTO]
}
