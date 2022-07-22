package models.graph

import silvousplay.imports._
import models.index.ValidEdgeConstraint

case class CreateEdge[From <: GenericNodeBuilder, To <: GenericNodeBuilder, Z <: GenericEdgeType](
  from: From,
  to:   To,
  z:    Z)(implicit val u: ValidEdge[From, To, Z]) {

  def edge(props: (String, String)*) = GenericEdgeBuilder(
    from,
    to,
    z,
    props.map {
      case (k, v) => GenericGraphProperty(k, v)
    }.toList)
}

// constraints
sealed abstract class ValidEdge[From <: GenericNodeBuilder, To <: GenericNodeBuilder, Z <: Identifiable]
  extends ValidEdgeConstraint[GenericGraphNodeType, From, To, Z]

object ValidEdge {

  implicit object tableRow extends ValidEdge[table.TableNode, table.RowNode, GenericEdgeType.TableRow.type]
  implicit object rowCell extends ValidEdge[table.RowNode, table.CellNode, GenericEdgeType.RowCell.type]

  // snapshot
  implicit object snapshotRow extends ValidEdge[snapshot.SnapshotNode, snapshot.SnapshotRowNode, GenericEdgeType.SnapshotRow.type]
  implicit object snapshotCell extends ValidEdge[snapshot.SnapshotRowNode, snapshot.SnapshotCellNode, GenericEdgeType.SnapshotRowCell.type]
  implicit object snapshotCellData extends ValidEdge[snapshot.SnapshotCellNode, snapshot.SnapshotCellDataNode, GenericEdgeType.SnapshotCellData.type]
  implicit object snapshotColumnCell extends ValidEdge[snapshot.SchemaColumnNode, snapshot.SnapshotCellNode, GenericEdgeType.SnapshotColumnCell.type]

  // annotations
  implicit object snapshotRowAnnotation extends ValidEdge[snapshot.SnapshotRowNode, snapshot.AnnotationNode, GenericEdgeType.SnapshotRowAnnotation.type]

  // schema
  implicit object schemaSnapshot extends ValidEdge[snapshot.SchemaNode, snapshot.SnapshotNode, GenericEdgeType.SchemaSnapshot.type]
  implicit object schemaColumn extends ValidEdge[snapshot.SchemaNode, snapshot.SchemaColumnNode, GenericEdgeType.SchemaColumn.type]

  // cross
  implicit object snapshotCodeIndex extends ValidEdge[snapshot.SnapshotNode, git.CodeIndex, GenericEdgeType.SnapshotCodeIndex.type]

  // git tree
  implicit object gitCommitParent extends ValidEdge[git.GitCommit, git.GitCommit, GenericEdgeType.GitCommitParent.type]
  implicit object gitHeadCommit extends ValidEdge[git.GitHead, git.GitCommit, GenericEdgeType.GitHeadCommit.type]
  implicit object gitCommitIndex extends ValidEdge[git.GitCommit, git.CodeIndex, GenericEdgeType.GitCommitIndex.type]

}
