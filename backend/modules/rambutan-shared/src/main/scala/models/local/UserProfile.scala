package models

import play.api.libs.json._

case class UserProfile(
  email:     String,
  superUser: Boolean,
  orgs:      List[OrganizationRole]) {
  def dto = UserProfileDTO(
    email,
    superUser,
    orgs.map(_.dto))
}

case class UserProfileDTO(
  email:     String,
  superUser: Boolean,
  orgs:      List[OrganizationRoleDTO])

object UserProfileDTO {
  implicit val writes = Json.writes[UserProfileDTO]
}
