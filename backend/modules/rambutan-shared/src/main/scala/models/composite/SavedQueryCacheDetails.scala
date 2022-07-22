package models

import play.api.libs.json._

case class SavedQueryKeyDetails(
  key:     QueryCacheKey,
  cache:   QueryCache,
  cursors: List[QueryCacheCursor]) {
  def dto = SavedQueryKeyDetailsDTO(
    cache.id,
    key.dto,
    cursors.map(_.dto))
}

case class SavedQueryCacheDetails(
  query: SavedQuery,
  keys:  List[SavedQueryKeyDetails]) {
  def dto = SavedQueryCacheDetailsDTO(
    query.id,
    query.name,
    keys.map(_.dto))
}

/**
 * DTOs
 */
case class SavedQueryKeyDetailsDTO(
  cacheId: Int,
  key:     QueryCacheKeyDTO,
  cursors: List[QueryCacheCursorDTO])

object SavedQueryKeyDetailsDTO {
  implicit val writes = Json.writes[SavedQueryKeyDetailsDTO]
}

case class SavedQueryCacheDetailsDTO(
  id:   Int,
  name: String,
  keys: List[SavedQueryKeyDetailsDTO])

object SavedQueryCacheDetailsDTO {
  implicit val writes = Json.writes[SavedQueryCacheDetailsDTO]
}
