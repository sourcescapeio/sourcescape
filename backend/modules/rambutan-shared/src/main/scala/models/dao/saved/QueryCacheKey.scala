package models

import play.api.libs.json._

case class QueryCacheKey(
  cacheId:      Int,
  key:          String,
  count:        Int,
  deleted:      Boolean,
  lastModified: Long) {

  def pk = (cacheId, key)

  def dto = QueryCacheKeyDTO(
    cacheId,
    key,
    count,
    lastModified)
}

case class QueryCacheKeyDTO(
  cacheId:      Int,
  key:          String,
  count:        Int,
  lastModified: Long)

object QueryCacheKeyDTO {
  implicit val writes = Json.writes[QueryCacheKeyDTO]
}