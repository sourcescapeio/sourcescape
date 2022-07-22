package models.index.ruby

import models.index._
import silvousplay.imports._
import play.api.libs.json._

case class CreateEdge[From <: RubyNode, To <: RubyNode, Z <: RubyEdgeType](
  from: From,
  to:   To,
  z:    Z)(implicit val u: ValidEdge[From, To, Z]) extends GraphCreateEdge[RubyNodeType, From, To, Z, CanIndex, CanName] {

  def shouldEmitToType = z.isContains
}
