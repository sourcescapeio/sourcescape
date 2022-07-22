package models

import silvousplay.imports._

abstract class RepoRole(val identifier: String, val cardinality: Int) extends Identifiable {
  def includes(other: RepoRole) = cardinality <= other.cardinality
}

object RepoRole extends Plenumeration[RepoRole] {
  case object Admin extends RepoRole("admin", 1)
  case object Push extends RepoRole("push", 2)
  case object Pull extends RepoRole("pull", 3)
  case object NoPerms extends RepoRole("none", 4)
}
