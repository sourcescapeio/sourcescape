package models.graph

case class ExpressionWrapper[+T <: GenericNodeBuilder](
  node:            T,
  children:        List[ExpressionWrapper[GenericNodeBuilder]],
  additionalNodes: List[GenericNodeBuilder],
  edges:           List[GenericEdgeBuilder]) {

  def allNodes: List[GenericNodeBuilder] = {
    node :: (additionalNodes ++ children.flatMap(_.allNodes))
  }

  def allEdges: List[GenericEdgeBuilder] = {
    edges ++ children.flatMap(_.allEdges)
  }
}

object ExpressionWrapper {
  def basic(n: GenericNodeBuilder) = ExpressionWrapper(n, Nil, Nil, Nil)
}
