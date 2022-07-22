package models

import silvousplay.imports._

abstract class OrgRole(val identifier: String, val cardinality: Int) extends Identifiable {
  def includes(other: OrgRole) = cardinality <= other.cardinality
}

object OrgRole extends Plenumeration[OrgRole] {
  case object Admin extends OrgRole("admin", 1)
  case object ReadOnly extends OrgRole("read-only", 2)
  case object NoPerms extends OrgRole("none", 3)
}
