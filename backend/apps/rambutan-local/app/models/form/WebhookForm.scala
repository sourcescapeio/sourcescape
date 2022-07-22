package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class WebhookForm(
  changedFiles: List[String],
  force:        Option[Boolean]) {
  val defaultedForce = force.getOrElse(false)
}

object WebhookForm {
  implicit val reads = Json.reads[WebhookForm]
}
