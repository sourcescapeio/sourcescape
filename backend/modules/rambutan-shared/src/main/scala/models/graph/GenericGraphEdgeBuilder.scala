package models.graph

import silvousplay.imports._
import play.api.libs.json._

case class GenericEdgeBuilder(
  from:     GenericNodeBuilder,
  to:       GenericNodeBuilder,
  edgeType: GenericEdgeType,
  props:    List[GenericGraphProperty]) {

  val idempotencyKey = withFlag(from.idempotent && to.idempotent) {
    Option(s"${edgeType.identifier}//${from.id}//${to.id}")
  }

  def json(orgId: Int) = idempotencyKey -> Json.toJson {
    GenericGraphEdge(
      id = Hashing.uuid(),
      orgId.toString(),
      edgeType,
      from.id,
      to.id,
      from.nodeType.identifier,
      to.nodeType.identifier,
      names = Nil,
      props = props)
  }
}
