package models

import silvousplay.imports._
import play.api.libs.json._

case class WorkSummary(
  count:       Int,
  collections: List[WorkTree]) {
  def dto = WorkSummaryDTO(
    count,
    collections.map(_.dto))
}

case class WorkSummaryDTO(
  count:       Int,
  collections: List[WorkTreeDTO])

object WorkSummaryDTO {
  implicit val writes = Json.writes[WorkSummaryDTO]
}
