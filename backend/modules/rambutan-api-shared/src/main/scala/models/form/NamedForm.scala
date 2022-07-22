package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class NamedForm(
  name: String)

object NamedForm {
  implicit val reads = Json.reads[NamedForm]
}
