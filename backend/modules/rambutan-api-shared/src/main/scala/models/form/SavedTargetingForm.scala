package models

import play.api.libs.json._
import silvousplay.imports._

case class SavedTargetingInnerForm(
  repoId: Int,
  branch: Option[String],
  sha:    Option[String])

object SavedTargetingInnerForm {
  implicit val reads = Json.reads[SavedTargetingInnerForm]
}

case class SavedTargetingForm(
  targeting:     Option[SavedTargetingInnerForm],
  fileFilter:    Option[String],
  targetingName: String)

object SavedTargetingForm {
  implicit val reads = Json.reads[SavedTargetingForm]
}
