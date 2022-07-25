package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class SchemaForm(
  title:        String,
  fieldAliases: Map[String, String],
  // query
  context:  SrcLogCodeQueryDTO,
  selected: List[String],
  named:    List[String],
  // targeting
  fileFilter:    Option[String],
  selectedRepos: List[Int])

object SchemaForm {
  implicit val reads = Json.reads[SchemaForm]
}

case class AnnotationForm(
  text:    String,
  rowKey:  String,
  indexId: Int)

object AnnotationForm {
  implicit val reads = Json.reads[AnnotationForm]
}
