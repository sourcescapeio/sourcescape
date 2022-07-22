package models

import play.api.libs.json._
import silvousplay.imports._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._

case class CreateOrgForm(
  name: String)

object CreateOrgForm {
  val form = Form(FormsMacro.mapping[CreateOrgForm])
}

case class UpdateOrgSettingsForm(
  dataDirectory: Option[String])

object UpdateOrgSettingsForm {
  val form = Form(FormsMacro.mapping[UpdateOrgSettingsForm])
}
