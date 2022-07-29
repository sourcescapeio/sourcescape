package models

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class RemoteType(val identifier: String) extends Identifiable

object RemoteType extends Plenumeration[RemoteType] {
  case object GitHub extends RemoteType("github")
  case object BitBucket extends RemoteType("bitbucket")
}

case class LocalRepoConfig(
  orgId:      Int,
  scanId:     Int,
  repoName:   String,
  repoId:     Int,
  localPath:  String,
  remote:     String,
  remoteType: RemoteType,
  branches:   List[String]
// not scanned
// intent:  Option[RepoCollectionIntent]
) extends GenericRepo {

  val dirtyPath = Some(localPath)

  def meta = {
    Json.obj(
      "local" -> localPath,
      "remote" -> remote)
  }
}
