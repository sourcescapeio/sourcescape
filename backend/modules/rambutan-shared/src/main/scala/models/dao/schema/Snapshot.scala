package models

import silvousplay.imports._
import play.api.libs.json._

abstract class SnapshotStatus(val identifier: String) extends Identifiable

object SnapshotStatus extends Plenumeration[SnapshotStatus] {

  case object Pending extends SnapshotStatus("pending")
  case object InProgress extends SnapshotStatus("in-progress")
  case object Complete extends SnapshotStatus("complete")

}

case class Snapshot(
  schemaId: Int,
  indexId:  Int,
  workId:   String,
  status:   SnapshotStatus) {

}

case class HydratedSnapshot(
  snapshot: Snapshot,
  index:    RepoSHAIndex) {
  def dto = HydratedSnapshotDTO(
    snapshot.schemaId,
    index.repoName,
    index.sha,
    index.id,
    snapshot.workId,
    snapshot.status)
}

// TODO: potentially temporary
case class HydratedSnapshotDTO(
  schemaId: Int,
  repoName: String,
  sha:      String,
  indexId:  Int,
  workId:   String,
  status:   SnapshotStatus)

object HydratedSnapshotDTO {
  implicit val writes = Json.writes[HydratedSnapshotDTO]
}
