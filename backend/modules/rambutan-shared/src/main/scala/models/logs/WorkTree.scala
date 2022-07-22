package models

import silvousplay.imports._
import play.api.libs.json._

case class WorkTree(
  parents:    List[WorkRecord],
  self:       WorkRecord,
  children:   List[WorkRecord],
  skippedMap: Map[String, List[String]],
  errorMap:   Map[String, List[String]]) {
  def dto: WorkTreeDTO = WorkTreeDTO(
    self.treeDTO(skippedMap, errorMap),
    parents.map(_.dto),
    children.map(_.treeDTO(skippedMap, errorMap)))
}

case class WorkTreeDTO(
  self:     WorkTreeRecordDTO,
  parents:  List[WorkRecordDTO],
  children: List[WorkTreeRecordDTO])

object WorkTreeDTO {
  implicit val writes = Json.writes[WorkTreeDTO]
}
