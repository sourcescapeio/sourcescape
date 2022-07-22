package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class TestIndexForm(
  text: String)

object TestIndexForm {
  implicit val reads = Json.reads[TestIndexForm]
}
