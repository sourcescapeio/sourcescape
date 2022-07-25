package models

import play.api.libs.json._
import silvousplay.imports._

case class RepoOpenForm(
  repoKey:   String,
  file:      String,
  startLine: Int)

object RepoOpenForm {
  implicit val reads = Json.reads[RepoOpenForm]
}
