package models

import play.api.libs.json._

case class QueryCacheCursor(
  cacheId: Int,
  key:     String,
  start:   Int,
  end:     Int,
  cursor:  JsValue) {

  def cursorModel = Json.fromJson[query.RelationalKey](cursor) match {
    case JsSuccess(item, _) => item
    case _                  => throw new Exception("bad deserialize on cursor")
  }

  def dto = QueryCacheCursorDTO(
    cacheId,
    key,
    start,
    end,
    cursor)
}

case class QueryCacheCursorDTO(
  cacheId: Int,
  key:     String,
  start:   Int,
  end:     Int,
  cursor:  JsValue)

object QueryCacheCursorDTO {
  implicit val writes = Json.writes[QueryCacheCursorDTO]
}
