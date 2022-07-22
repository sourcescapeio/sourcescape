package models.query

import models.query.display._

import silvousplay.imports._

case class BuilderEdge(
  edgeType: BuilderEdgeType,
  id:       String,
  name:     Option[String],
  index:    Option[Int],
  tree:     BuilderTree) {

  def print(indents: Int): Unit = {
    println((" " * indents) + ">>" + edgeType)
    tree.print(indents + 2)
  }

  def allIds: List[String] = {
    id :: tree.allIds
  }

  def allRequire: List[String] = {
    if (edgeType =?= BuilderEdgeType.RequireDependency) {
      tree.node.id :: Nil
    } else {
      tree.children.flatMap(_.allRequire)
    }
  }

  def forceReferences(referenceSet: Set[String]) = {
    this.copy(
      tree = tree.forceReferences(referenceSet))
  }

  def splice(edge: EdgeClause, newTree: BuilderTree): BuilderEdge = {
    this.copy(tree = tree.splice(edge, newTree))
  }

  def display(parentId: String): (BuilderEdgeType, DisplayStructContainer) = {
    (edgeType, DisplayStructContainer(tree.display(parentId), name, index))
  }

  def followReferences = withFlag(edgeType.priority.reference) {
    Option(this.copy(tree = tree.followReferences))
  }
}

object BuilderEdge {
  def fromClause(clause: EdgeClause, tree: BuilderTree) = {
    BuilderEdge(
      BuilderEdgeType.fromPredicate(clause.predicate),
      clause.to,
      clause.condition.flatMap {
        case NameCondition(value) => Some(value)
        case _                    => None
      },
      clause.condition.flatMap {
        case IndexCondition(value) => Some(value.toInt)
        case _                     => None
      },
      tree)
  }
}
