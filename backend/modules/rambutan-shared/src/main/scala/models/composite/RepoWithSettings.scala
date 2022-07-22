package models

import silvousplay.imports._
import play.api.libs.json._

trait GenericRepo {
  val repoId: Int
  val repoName: String
  val orgId: Int
  val branches: List[String]

  val dirtyPath: Option[String]

  def meta: JsObject

  def withSettingsEmpty = withSettings(Set.empty[String], Map.empty[Int, Option[RepoSettings]])

  def withSettings(uniqueSet: Set[String], settingsMap: Map[Int, Option[RepoSettings]]) = {
    RepoWithSettings(
      this,
      uniqueSet.contains(repoName),
      settingsMap.getOrElse(repoId, None))
  }
}

case class RepoWithSettings(
  repo:       GenericRepo,
  uniqueName: Boolean,
  settings:   Option[RepoSettings]) {
  val defaultedIntent = settings.map(_.intent).getOrElse(RepoCollectionIntent.Skip)

  def repoId = repo.repoId

  def shouldIndex: Boolean = {
    defaultedIntent =?= RepoCollectionIntent.Collect
  }

  def unified(
    indexes:       Map[Int, List[RepoSHAIndex]],
    latestIndexes: Map[Int, Option[RepoSHAIndex]],
    logs:          Map[String, WorkRecord],
    latestSHAs:    Map[Int, RepoSHA]) = {

    UnifiedRepoSummary(
      repo.orgId,
      repo.repoName,
      repo.repoId,
      uniqueName,
      branches = repo.branches,
      latestBranches = latestSHAs.get(repo.repoId).map(_.branches).getOrElse(Nil),
      indexes = indexes.getOrElse(repo.repoId, Nil).map { ii =>
        UnifiedIndexItem(
          ii.id,
          logs.get(ii.workId).map(_.status))
      },
      intent = defaultedIntent,
      meta = repo.meta)
  }
}
