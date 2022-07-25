package models

import play.api.libs.json._
import silvousplay.imports._

case class TargetingForm(
  repoId: Option[Int],
  branch: Option[String],
  sha:    Option[String])

object TargetingForm {
  implicit val reads = Json.reads[TargetingForm]
}
