package models

import silvousplay.imports._
import play.api.libs.json._

case class SavedTargeting(
  id:         Int,
  orgId:      Int,
  name:       String,
  repoId:     Option[Int],
  branch:     Option[String],
  sha:        Option[String],
  fileFilter: Option[String]) {

  def dto = SavedTargetingDTO(
    id,
    orgId,
    name,
    repoId.map { r =>
      Json.obj("repoId" -> r, "branch" -> branch, "sha" -> sha)
    },
    fileFilter)
}

case class SavedTargetingDTO(
  id:         Int,
  orgId:      Int,
  name:       String,
  targeting:  Option[JsValue],
  fileFilter: Option[String])

object SavedTargetingDTO {
  implicit val writes = Json.writes[SavedTargetingDTO]
}
