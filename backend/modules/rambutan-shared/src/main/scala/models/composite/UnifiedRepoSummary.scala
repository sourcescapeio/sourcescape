package models

import silvousplay.imports._
import play.api.libs.json._

case class UnifiedRepoSummary(
  orgId:          Int,
  repo:           String,
  repoId:         Int,
  unique:         Boolean,
  branches:       List[String],
  latestBranches: List[String],
  indexes:        List[UnifiedIndexItem],
  intent:         RepoCollectionIntent,
  meta:           JsObject) {

  def shouldIndex: Boolean = {
    intent =?= RepoCollectionIntent.Collect
  }

  def dto = UnifiedRepoSummaryDTO(
    repo,
    repoId,
    unique,
    branches,
    latestBranches,
    indexes.map(_.dto),
    intent,
    meta)
}

case class UnifiedIndexItem(
  indexId: Int,
  status:  Option[WorkStatus]) {
  def dto = UnifiedIndexItemDTO(indexId, status)
}

case class UnifiedIndexItemDTO(
  indexId: Int,
  status:  Option[WorkStatus])

object UnifiedIndexItemDTO {
  implicit val writes = Json.writes[UnifiedIndexItemDTO]
}

case class UnifiedRepoSummaryDTO(
  repo:           String,
  repoId:         Int,
  unique:         Boolean,
  branches:       List[String],
  latestBranches: List[String],
  indexes:        List[UnifiedIndexItemDTO],
  intent:         RepoCollectionIntent,
  meta:           JsObject)

object UnifiedRepoSummaryDTO {
  implicit val writes = Json.writes[UnifiedRepoSummaryDTO]
}
