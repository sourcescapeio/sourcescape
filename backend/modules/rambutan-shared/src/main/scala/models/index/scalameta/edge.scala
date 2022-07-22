package models.index.scalameta

import models.index._
import silvousplay.imports._
import play.api.libs.json._

case class CreateEdge[From <: ScalaMetaNode, To <: ScalaMetaNode, Z <: ScalaMetaEdgeType](
  from: From,
  to:   To,
  z:    Z)(implicit val u: ValidEdge[From, To, Z]) extends GraphCreateEdge[ScalaMetaNodeType, From, To, Z, CanIndex, CanName] {

  def shouldEmitToType = z.isContains
}
