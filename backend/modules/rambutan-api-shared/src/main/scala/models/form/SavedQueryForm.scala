package models

import models.query._
import play.api.libs.json._
import silvousplay.imports._

case class SavedQueryForm(
  context:   SrcLogCodeQueryDTO,
  selected:  String,
  queryName: String) {

  // def toModel = BuilderState(
  //   context.toModel,
  //   selected,
  //   trace,
  //   update = false)
}

object SavedQueryForm {
  implicit val reads = Json.reads[SavedQueryForm]
}

