package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class TestIndexForm(
  file:    String,
  content: String)

object TestIndexForm {
  implicit val reads = Json.reads[TestIndexForm]
}
