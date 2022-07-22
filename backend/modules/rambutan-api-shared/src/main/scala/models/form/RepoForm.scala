package models

import play.api.libs.json._
import silvousplay.imports._

case class RepoForm(
  repos: List[Int])

object RepoForm {
  implicit val reads = Json.reads[RepoForm]
}
