package models.index

import silvousplay.imports._

trait GraphEdgeBuilder {
  def build(orgId: Int, repoName: String, repoId: Int, indexId: Int, path: String): GraphEdge
}

case class StandardEdgeBuilder[+ET <: Identifiable, +NT <: Identifiable](
  from:     String,
  to:       String,
  edgeType: ET,
  toType:   Option[NT],
  index:    Option[Int],
  name:     Option[String]) extends GraphEdgeBuilder {

  def build(orgId: Int, repoName: String, repoId: Int, indexId: Int, path: String): GraphEdge = {
    val key = models.RepoSHAHelpers.esKey(orgId, repoName, repoId, indexId)

    GraphEdge(
      key,
      path,
      edgeType.identifier,
      from,
      to,
      Hashing.uuid,
      toType.map(_.identifier),
      name = name.map(_.take(GraphNode.NameLimit)),
      index = index)
  }
}

abstract class GraphCreateEdge[NT <: Identifiable, From <: GraphNodeData[NT], To <: GraphNodeData[NT], Z <: Identifiable, CI[_ <: Z], CN[_ <: Z]](implicit v: ValidEdgeConstraint[NT, From, To, Z]) {
  def from: From
  def to: To
  def z: Z

  def shouldEmitToType: Boolean

  def emittedNodeType = withFlag(shouldEmitToType) {
    Option(to.nodeType)
  }

  def edge = {
    StandardEdgeBuilder(
      from.id,
      to.id,
      z,
      emittedNodeType,
      index = None,
      name = None)
  }

  def indexed(idx: Int)(implicit can: CI[Z]) = {
    StandardEdgeBuilder(
      from.id,
      to.id,
      z,
      emittedNodeType,
      index = Some(idx),
      name = None)
  }

  def named(name: String)(implicit can: CN[Z]) = {
    StandardEdgeBuilder(
      from.id,
      to.id,
      z,
      emittedNodeType,
      index = None,
      name = Some(name))
  }
}
