package models

import silvousplay.imports._
import play.api.libs.json._

case class DirtySignature(
  modified: Map[String, String],
  deleted:  List[String])

object DirtySignature {
  implicit val format = Json.format[DirtySignature]
}

case class RepoSHAIndex(
  id:          Int,
  orgId:       Int, // we store these to make calculating the keys much easier
  repoName:    String, // same as above
  repoId:      Int,
  sha:         String,
  rootIndexId: Option[Int],
  // dirty:       Boolean,
  dirtySignature: Option[JsValue],
  workId:         String,
  deleted:        Boolean,
  created:        Long) {

  def isDiff = rootIndexId.isDefined
  def isRoot = !isDiff

  def esKey = RepoSHAHelpers.esKey(orgId, repoName, repoId, id)
  def rootKey = rootIndexId.map(rootId => RepoSHAHelpers.esKey(orgId, repoName, repoId, rootId))

  def dirty = dirtySignature.isDefined

  def compare(other: DirtySignature) = {
    dirtySignature.flatMap(_.asOpt[DirtySignature]) match {
      case Some(d) => d.modified =?= other.modified && d.deleted =?= other.deleted
      case _       => false
    }
  }

  // def directoryKey = s"${orgId}/${repoId}/${id}"

  def dto(latestIndexId: Option[Int]) = RepoSHAIndexDTO(
    id,
    isDiff,
    Option(id) =?= latestIndexId,
    dirty,
    workId // need to hydrate
  )

  def collectionsDirectory = s"${RepoSHAHelpers.CollectionsDirectory}/${esKey}"
  def analysisDirectory = s"analysis/${esKey}"

}

case class RepoSHAIndexDTO(
  id:       Int,
  isDiff:   Boolean,
  isLatest: Boolean,
  dirty:    Boolean,
  workId:   String)

object RepoSHAIndexDTO {
  implicit val writes = Json.writes[RepoSHAIndexDTO]
}
