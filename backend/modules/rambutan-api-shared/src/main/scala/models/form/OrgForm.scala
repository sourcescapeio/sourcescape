package models

import play.api.libs.json._
import silvousplay.imports._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._

case class CreatePrivateOrgForm(
  name:   String,
  devKey: String)

object CreatePrivateOrgForm {
  val form = Form(FormsMacro.mapping[CreatePrivateOrgForm])
}
