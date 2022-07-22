package models

import play.api.libs.json._

case class DocumentationBlock(
  id:       Int,
  orgId:    Int,
  parentId: Int,
  text:     String)
