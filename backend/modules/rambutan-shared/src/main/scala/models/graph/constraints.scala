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

  // git tree
  implicit object gitCommitParent extends ValidEdge[git.GitCommit, git.GitCommit, GenericEdgeType.GitCommitParent.type]
  implicit object gitHeadCommit extends ValidEdge[git.GitHead, git.GitCommit, GenericEdgeType.GitHeadCommit.type]
  implicit object gitCommitIndex extends ValidEdge[git.GitCommit, git.CodeIndex, GenericEdgeType.GitCommitIndex.type]

}
