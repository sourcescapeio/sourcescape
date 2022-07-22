package models

import silvousplay.imports._
import play.api.libs.json._

case class RepoSHA(
  repoId:   Int,
  sha:      String,
  parents:  List[String],
  branches: List[String],
  refs:     List[String],
  message:  String) {

  private def extractTask(children: List[WorkRecord], task: String) = {
    children.filter(c => (c.tags \ "task").asOpt[String] =?= Some(task)).sortBy(_.started).reverse.headOption
  }

  def dto = HydratedRepoSHA(this, Nil, None).dto
}

case class HydratedRepoSHA(
  sha:           RepoSHA,
  indexes:       List[RepoSHAIndex],
  latestIndexId: Option[Int]) {
  def dto = RepoSHADTO(
    sha.repoId,
    sha.sha,
    sha.parents,
    sha.refs,
    sha.message,
    indexes.exists(i => Option(i.id) =?= latestIndexId),
    indexes.map(_.dto(latestIndexId)))
}

object RepoSHAHelpers {
  val CollectionsDirectory = "collections"

  def esKey(orgId: Int, repoName: String, repoId: Int, indexId: Int) = {
    s"${orgId}/${repoName}/${repoId}/${indexId}"
  }

  def getRepoId(key: String) = {
    key.split("/").takeRight(2) match {
      case Array(repoId, indexId) => repoId.toInt
    }
  }
}

case class RepoSHADTO(
  repoId:  Int,
  sha:     String,
  parents: List[String],
  refs:    List[String],
  message: String,
  latest:  Boolean,
  indexes: List[RepoSHAIndexDTO])

object RepoSHADTO {
  implicit val writes = Json.writes[RepoSHADTO]
}
