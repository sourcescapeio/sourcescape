package models.index.esprima

import models.index._
import silvousplay.imports._
import play.api.libs.json._

case class CreateEdge[From <: ESPrimaNode, To <: ESPrimaNode, Z <: ESPrimaEdgeType](
  from: From,
  to:   To,
  z:    Z)(implicit val u: ValidEdge[From, To, Z]) extends GraphCreateEdge[ESPrimaNodeType, From, To, Z, CanIndex, CanName] {

  def shouldEmitToType = z.isContains
}
