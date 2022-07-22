package models

import silvousplay.imports._

sealed abstract class RepoCollectionIntent(val identifier: String) extends Identifiable

object RepoCollectionIntent extends Plenumeration[RepoCollectionIntent] {
  case object Collect extends RepoCollectionIntent("collect")
  case object Skip extends RepoCollectionIntent("skip")
}
