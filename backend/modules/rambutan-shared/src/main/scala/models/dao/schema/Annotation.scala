package models

import play.api.libs.json._

case class Annotation(
  id:      Int,
  colId:   Int,
  indexId: Int,
  rowKey:  String,
  payload: JsValue)
