package models.query

import play.api.libs.json._
import silvousplay.imports._

case class BuilderOperation(
  `type`:  BuilderOperationType,
  payload: JsValue)

object BuilderOperation {
  implicit val reads = Json.reads[BuilderOperation]
}

sealed abstract class BuilderOperationType(val identifier: String) extends Identifiable

object BuilderOperationType extends Plenumeration[BuilderOperationType] {

  case object SetQuery extends BuilderOperationType("set-query")
  case object SetSrcLog extends BuilderOperationType("set-srclog")

  case object AddPayload extends BuilderOperationType("add-payload")
  case object AddArg extends BuilderOperationType("add-arg") // specialized because we detect trees

  case object Delete extends BuilderOperationType("delete")
  case object DeleteEdge extends BuilderOperationType("delete-edge")

  case object SetName extends BuilderOperationType("set-name")
  case object UnsetName extends BuilderOperationType("unset-name")
  case object SetAlias extends BuilderOperationType("set-alias")

  case object SetIndex extends BuilderOperationType("set-index")
  case object UnsetIndex extends BuilderOperationType("unset-index")
  case object PrependIndex extends BuilderOperationType("prepend-index")

  case object Select extends BuilderOperationType("select")
  case object Deselect extends BuilderOperationType("deselect")

}
