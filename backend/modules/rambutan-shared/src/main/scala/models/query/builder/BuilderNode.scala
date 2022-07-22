package models.query

import models.query.display._
import silvousplay.imports._

case class BuilderNode(
  nodeType:       BuilderNodeType,
  id:             String,
  name:           Option[String],
  index:          Option[Int],
  alias:          Option[String],
  forceReference: Boolean) {

  def print = {
    val maybeAlias = alias.map(a => s",alias=${a}").getOrElse("")
    val maybeName = name.map(n => s",name=${n}").getOrElse("")
    s"n:${nodeType}[id=${id}${maybeAlias}${maybeName}]"
  }

  def forceReferences(referenceSet: Set[String]) = {
    if (referenceSet.contains(id)) {
      this.copy(forceReference = true)
    } else {
      this
    }
  }

  def display(parentId: String, children: Map[BuilderEdgeType, List[DisplayStructContainer]]) = {
    val base = nodeType.displayStruct(id, parentId, name, index, alias)

    if (forceReference && nodeType =/= BuilderNodeType.Reference) {
      DisplayContainer.forceContainer(base, children)
    } else {
      base.applyChildren(children)
    }
  }
}

object BuilderNode {
  def fromClause(clause: NodeClause) = {
    BuilderNode(
      BuilderNodeType.fromPredicate(clause.predicate),
      clause.variable,
      clause.condition.flatMap {
        case NameCondition(value) => Some(value)
        case _                    => None
      },
      clause.condition.flatMap {
        case IndexCondition(value) => Some(value.toInt)
        case _                     => None
      },
      alias = None,
      forceReference = false)
  }
}
