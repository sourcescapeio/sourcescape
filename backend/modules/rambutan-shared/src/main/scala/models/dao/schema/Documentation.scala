package models

import play.api.libs.json._

case class Documentation(
  id:       Int,
  orgId:    Int,
  parentId: Option[Int])

case class HydratedDocumentation(
  documentation: Documentation,
  blocks:        List[DocumentationBlock]) {

  def dto = HydratedDocumentationDTO(
    documentation.id,
    // title
    blocks.map { b =>
      Json.obj(
        "id" -> b.id,
        "text" -> b.text)
    })
}

case class HydratedDocumentationDTO(
  id: Int,
  // title
  blocks: List[JsValue])

object HydratedDocumentationDTO {
  implicit val writes = Json.writes[HydratedDocumentationDTO]
}
