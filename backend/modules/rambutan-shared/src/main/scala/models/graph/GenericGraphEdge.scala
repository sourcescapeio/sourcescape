package models.graph

import models.index.EdgeType
import play.api.libs.json._
import silvousplay.imports._

sealed abstract class GenericEdgeType(val identifier: String) extends EdgeType

object GenericEdgeType extends Plenumeration[GenericEdgeType] {

  case object Test extends GenericEdgeType("test")

  case object TableRow extends GenericEdgeType("row")
  case object RowCell extends GenericEdgeType("cell")

  // snapshots
  case object SchemaSnapshot extends GenericEdgeType("schema-snapshot")
  case object SchemaColumn extends GenericEdgeType("schema-column")

  case object SnapshotRow extends GenericEdgeType("snapshot-row")
  case object SnapshotRowCell extends GenericEdgeType("snapshot-row-cell")
  case object SnapshotColumnCell extends GenericEdgeType("snapshot-column-cell")
  case object SnapshotCellData extends GenericEdgeType("snapshot-cell-data")

  case object SnapshotRowAnnotation extends GenericEdgeType("snapshot-row-annotation")

  case object SnapshotCodeIndex extends GenericEdgeType("snapshot-code-index")

  val snapshot = List(
    SchemaSnapshot,
    SchemaColumn,
    SnapshotRow,
    SnapshotRowCell,
    SnapshotColumnCell,
    SnapshotCellData,
    SnapshotRowAnnotation,
    SnapshotCodeIndex)

  // git
  case object GitCommitParent extends GenericEdgeType("git-commit-parent")
  case object GitHeadCommit extends GenericEdgeType("git-head-commit")
  case object GitCommitIndex extends GenericEdgeType("git-commit-index")

}

case class GenericGraphEdge(
  id:       String,
  org_id:   String,
  `type`:   GenericEdgeType,
  from:     String,
  to:       String,
  fromType: String,
  toType:   String,
  names:    List[String],
  props:    List[GenericGraphProperty])

object GenericGraphEdge {
  val globalIndex = "generic_edge"

  val BaseMappings = Json.obj(
    "dynamic" -> false,
    "properties" -> Json.obj(
      "id" -> Json.obj(
        "type" -> "keyword"),
      "org_id" -> Json.obj(
        "type" -> "keyword"),
      "type" -> Json.obj(
        "type" -> "keyword"),
      "from" -> Json.obj(
        "type" -> "keyword"),
      "to" -> Json.obj(
        "type" -> "keyword"),
      "fromType" -> Json.obj(
        "type" -> "keyword"),
      "toType" -> Json.obj(
        "type" -> "keyword"),
      "names" -> Json.obj(
        "type" -> "text",
        "analyzer" -> "keyword"), // better analyzer here?
      "props" -> Json.obj(
        "type" -> "keyword")))

  val mappings = Json.obj(
    "settings" -> Json.obj(
      "index" -> Json.obj(
        "sort.field" -> List("org_id", "from", "id"),
        "sort.order" -> List("asc", "asc", "asc"))),
    "mappings" -> BaseMappings)

  // We don't use reads. Manually reading the JSON
  implicit val writes = Json.writes[GenericGraphEdge]
}
