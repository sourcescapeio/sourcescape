package models.query

object SrcLogString {

  def stringifyNode(node: NodeClause) = {
    val conditionStanza = node.condition match {
      case Some(NameCondition(v))  => "[name = \"" + v + "\"]"
      case Some(IndexCondition(v)) => s"[index = ${v}]"
      case _                       => ""
    }
    s"${node.predicate.identifier}(${node.variable})" + conditionStanza + "."
  }

  def stringifyEdge(edge: EdgeClause) = {
    val conditionStanza = edge.condition match {
      case Some(NameCondition(v))  => "[name = \"" + v + "\"]"
      case Some(IndexCondition(v)) => s"[index = ${v}]"
      case _                       => ""
    }

    s"${edge.predicate.identifier}(${edge.from}, ${edge.to})" + conditionStanza + "."
  }

  def stringifyAlias(kv: (String, String)) = {
    val (k, v) = kv
    s"%alias(${k}=${v})."
  }

  def stringify(query: SrcLogCodeQuery) = {
    val clauses = (query.nodes.map(stringifyNode) ++ query.edges.map(stringifyEdge) ++ query.aliases.map(stringifyAlias))
    clauses.mkString("\n\n")
  }
}