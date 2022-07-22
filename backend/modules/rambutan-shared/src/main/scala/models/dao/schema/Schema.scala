package models

import models.query._
import play.api.libs.json._

case class Schema(
  id:    Int,
  orgId: Int,
  title: String,
  // query
  fieldAliases: JsValue,
  savedQueryId: Int,
  selected:     List[String],
  named:        List[String],
  // query computed
  sequence: List[String],
  // targeting
  fileFilter:    Option[String],
  selectedRepos: List[Int]) {

  def dto = SchemaDTO(id, title)

  def fieldAliasesMap = fieldAliases.as[Map[String, String]]
}

case class SchemaDTO(id: Int, title: String)

object SchemaDTO {
  implicit val writes = Json.writes[SchemaDTO]
}

case class SchemaWithQuery(
  schema:     Schema,
  savedQuery: SavedQuery)

// heyo
case class HydratedSchema(
  schema:      Schema,
  savedQuery:  SavedQuery,
  latest:      Option[HydratedSnapshot],
  annotations: List[AnnotationColumn]) {

  def dto = HydratedSchemaDTO(
    schema.id,
    schema.title,
    schema.fieldAliasesMap,
    latest.map(_.index.id),
    latest.map(_.dto),
    savedQuery.dto.context,
    schema.sequence,
    annotations.map(_.dto))
}

case class HydratedSchemaDTO(
  id:             Int,
  title:          String,
  aliases:        Map[String, String],
  latestIndexId:  Option[Int],
  latestSnapshot: Option[HydratedSnapshotDTO],
  context:        BuilderStateDTO,
  selected:       List[String],
  annotations:    List[AnnotationColumnDTO])

object HydratedSchemaDTO {
  implicit val writes = Json.writes[HydratedSchemaDTO]
}
