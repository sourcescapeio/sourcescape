package models.graph

import play.api.libs.json._
import silvousplay.imports._
import models.index.NodeType

sealed abstract class GenericGraphNodeType(val identifier: String) extends NodeType

object GenericGraphNodeType {
  // Tables
  case object Table extends GenericGraphNodeType("table")
  case object Row extends GenericGraphNodeType("row")
  case object Cell extends GenericGraphNodeType("cell")

  // Snapshots
  case object Schema extends GenericGraphNodeType("schema")
  case object SchemaColumn extends GenericGraphNodeType("schema-column")

  case object Snapshot extends GenericGraphNodeType("snapshot")
  case object SnapshotRow extends GenericGraphNodeType("snapshot-row")
  case object SnapshotCell extends GenericGraphNodeType("snapshot-cell")
  case object SnapshotCellData extends GenericGraphNodeType("snapshot-cell-data")

  case object Annotation extends GenericGraphNodeType("annotation")

  val snapshot = List(
    Schema,
    SchemaColumn,
    Snapshot,
    SnapshotRow,
    SnapshotCell,
    SnapshotCellData,
    Annotation)

  // Git
  case object GitCommit extends GenericGraphNodeType("git-commit")
  case object GitHead extends GenericGraphNodeType("git-head")
  case object CodeIndex extends GenericGraphNodeType("code-index")

}

case class GenericGraphNode(
  id:      String,
  org_id:  String,
  `type`:  String,
  names:   List[String],
  props:   List[GenericGraphProperty],
  payload: JsValue) {

  def getProp(key: String) = props.find(_.key =?= key).map(_.value)

}

object GenericGraphNode {
  val globalIndex = "generic_node"

  // shard by (orgId)
  val mappings = Json.obj(
    "settings" -> Json.obj(
      "index" -> Json.obj(
        "sort.field" -> List("org_id", "id"),
        "sort.order" -> List("asc", "asc"))),
    "mappings" -> Json.obj(
      "dynamic" -> false,
      "properties" -> Json.obj(
        "id" -> Json.obj(
          "type" -> "keyword"),
        "org_id" -> Json.obj(
          "type" -> "keyword"),
        "type" -> Json.obj(
          "type" -> "keyword"),
        "names" -> Json.obj(
          "type" -> "text",
          "analyzer" -> "keyword"), // better analyzer here?
        "props" -> Json.obj(
          "type" -> "keyword"),
        "payload" -> Json.obj(
          "enabled" -> false))))

  implicit val format = Json.format[GenericGraphNode]
}
