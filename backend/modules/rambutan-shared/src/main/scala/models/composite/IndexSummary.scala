package models

import play.api.libs.json._

case class ESIndexSummary(
  indexName: String,
  health:    String,
  size:      String,
  count:     String) {
  def dto = ESIndexSummaryDTO(
    indexName,
    health,
    size,
    count)
}

case class ESIndexSummaryDTO(
  indexName: String,
  health:    String,
  size:      String,
  count:     String)

object ESIndexSummaryDTO {
  implicit val writes = Json.writes[ESIndexSummaryDTO]
}

case class IndexSummary(
  indexType: IndexType,
  node:      Option[ESIndexSummary],
  edge:      Option[ESIndexSummary]) {
  def dto = IndexSummaryDTO(
    indexType,
    node.map(_.dto),
    edge.map(_.dto))
}

case class IndexSummaryDTO(
  indexType: IndexType,
  node:      Option[ESIndexSummaryDTO],
  edge:      Option[ESIndexSummaryDTO])

object IndexSummaryDTO {
  implicit val writes = Json.writes[IndexSummaryDTO]
}
