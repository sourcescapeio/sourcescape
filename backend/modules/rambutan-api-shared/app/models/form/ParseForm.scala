package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class ParseForm(
  context:       SrcLogCodeQueryDTO,
  selected:      Option[String],
  traceSelected: Option[Map[String, Boolean]],
  trace:         Boolean) {
}

object ParseForm {
  implicit val reads = Json.reads[ParseForm]
}
