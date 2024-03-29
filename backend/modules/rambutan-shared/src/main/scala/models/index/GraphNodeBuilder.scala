package models.index

import models.CodeRange
import silvousplay.imports._

trait GraphNodeBuilder {

  def build(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int, path: String): GraphNode

  // used for looking up symbols and linking
  def lookupIndex: Option[Int]
  def definitionLink: Option[String]
  def typeDefinitionLink: Option[String]

  // will generate a symbol entry
  def generateSymbol: Boolean
}

trait GraphNodeData[NT <: Identifiable] {
  def id: String
  def nodeType: NT
}

trait StandardNodeBuilder[NT <: Identifiable, T <: Identifiable] extends GraphNodeBuilder {
  self: GraphNodeData[NT] =>

  val nodeType: NT

  val range: CodeRange

  def names: List[String]
  val index: Option[Int]

  val tags: List[T]

  override def build(orgId: Int, repoName: String, repoId: Int, sha: String, indexId: Int, path: String) = {
    val key = models.RepoSHAHelpers.esKey(orgId, repoName, repoId, indexId)
    GraphNode(
      id,
      repoName,
      sha,
      key,
      path,
      nodeType.identifier,
      start_line = range.start.line,
      end_line = range.end.line,
      start_column = range.start.column,
      end_column = range.end.column,
      start_index = range.startIndex,
      end_index = range.endIndex,
      // keys
      name = names.headOption,
      search_name = names.map(_.take(GraphNode.NameLimit)).toList,
      props = Nil, // TODO
      tags = tags.map(_.identifier),
      index = index)
  }
}