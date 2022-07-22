package models

import play.api.libs.json._

case class LocalOrgSettings(
  orgId:        Int,
  completedNux: Boolean)
