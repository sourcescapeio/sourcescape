package models.graph

import models.index.{ GraphNodeData, NodeType, EdgeType }
import silvousplay.imports._
import play.api.libs.json._

// node builder
abstract class GenericNodeBuilder(
  val nodeType:   GenericGraphNodeType,
  props:          List[GenericGraphProperty] = Nil,
  idempotencyKey: Option[String]             = None,
  payload:        JsValue                    = Json.obj()) extends GraphNodeData[GenericGraphNodeType] {

  val idempotent = idempotencyKey.isDefined

  def json(orgId: Int) = {
    idempotencyKey -> Json.toJson {
      GenericGraphNode(
        id,
        orgId.toString(),
        nodeType.identifier,
        names = Nil,
        props = props,
        payload = payload)
    }
  }

  lazy val id = idempotencyKey.getOrElse {
    Hashing.uuid()
  }
}
