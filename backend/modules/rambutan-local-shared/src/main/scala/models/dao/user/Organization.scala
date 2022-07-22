package models

import silvousplay.imports._
import play.api.libs.json._

case class Organization(
  id:      Int,
  ownerId: Int, // user
  name:    String) {

  def withRole(isAdmin: Boolean) = {
    val role = if (isAdmin) {
      UserRole.Admin
    } else {
      UserRole.ReadOnly
    }

    OrganizationRole(this, role)
  }
}

// UserProject

abstract class UserRole(val identifier: String, val cardinality: Int) extends Identifiable {
  def includes(other: UserRole) = cardinality <= other.cardinality
}

object UserRole extends Plenumeration[UserRole] {
  case object Admin extends UserRole("admin", 1)
  case object ReadOnly extends UserRole("read-only", 2)
  case object Public extends UserRole("public", 3) // readonly, but for org -1, always open
}

case class OrganizationRole(
  org:  Organization,
  role: UserRole) {
  def dto = OrganizationRoleDTO(
    org.id,
    org.name,
    role)
}

case class OrganizationRoleDTO(
  id:   Int,
  name: String,
  role: UserRole)

object OrganizationRoleDTO {
  implicit val writes = Json.writes[OrganizationRoleDTO]
}
