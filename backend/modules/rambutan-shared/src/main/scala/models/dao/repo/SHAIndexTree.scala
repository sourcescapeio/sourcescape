package models

import play.api.libs.json._

case class SHAIndexTree(
  indexId: Int,
  file:    String,
  deleted: Boolean)

case class SHAIndexTreeListing(
  path:     String,
  fullPath: String,
  children: List[SHAIndexTreeListing]) {
  val isDirectory = children.nonEmpty

  def dto: SHAIndexTreeListingDTO = SHAIndexTreeListingDTO(
    path,
    fullPath,
    children.map(_.dto),
    isDirectory)
}

case class SHAIndexTreeListingDTO(
  path:      String,
  fullPath:  String,
  children:  List[SHAIndexTreeListingDTO],
  directory: Boolean)

object SHAIndexTreeListingDTO {
  implicit val writes = Json.writes[SHAIndexTreeListingDTO]
}
