package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class CacheForm(
  context:    SrcLogCodeQueryDTO,
  selected:   String,
  indexIds:   Option[List[Int]],
  fileFilter: Option[String],
  queryId:    Option[Int])

object CacheForm {
  implicit val reads = Json.reads[CacheForm]
}
