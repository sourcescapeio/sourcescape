package models

import play.api.libs.json._
import silvousplay.imports._

sealed abstract class AnnotationColumnType(val identifier: String) extends Identifiable

object AnnotationColumnType extends Plenumeration[AnnotationColumnType] {

  case object Documentation extends AnnotationColumnType("documentation")
}

case class AnnotationColumn(
  id:         Int,
  schemaId:   Int,
  name:       String,
  columnType: AnnotationColumnType) {

  val srcLogName = s"AC${id}"

  def validate(payload: JsValue) = {

  }

  def dto = AnnotationColumnDTO(id, name, columnType)
}

case class AnnotationColumnDTO(
  id:         Int,
  name:       String,
  columnType: AnnotationColumnType)

object AnnotationColumnDTO {
  implicit val writes = Json.writes[AnnotationColumnDTO]
}
