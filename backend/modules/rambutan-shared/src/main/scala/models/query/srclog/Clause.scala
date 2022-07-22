package models.query

import models.IndexType
import silvousplay.imports._
import play.api.libs.json._

sealed abstract class BooleanModifier(val identifier: String) extends Identifiable

object BooleanModifier extends Plenumeration[BooleanModifier] {
  // case object Negate extends BooleanModifier("negate")
  case object Optional extends BooleanModifier("optional")
}

sealed trait SrcLogClause

case class NodeClause(predicate: NodePredicate, variable: String, condition: Option[Condition]) extends SrcLogClause {
  // private def name = condition flatMap {
  //   case NameCondition(v) => Some(v)
  //   case _                      => None
  // }.headOption

  // private def index = condition flatMap {
  //   case IndexCondition(v) => Some(v.toInt)
  //   case _                       => None
  // }.headOption

  private def filters = {
    predicate.filters(condition)
  }

  // used externally for count
  def getRoot = {
    GraphRoot(filters)
  }

  // private def additionalQuery = {
  //   predicate.additionalQuery(name, index)
  // }

  def getQuery = {
    GraphQuery(
      getRoot,
      Nil)
  }

  def nodeTraverse = {
    // everything gets references, even if not necessary
    // not a big deal?
    ifNonEmpty(filters) {
      NodeTraverse(
        follow = EdgeTypeFollow(EdgeTypeTraverse.BasicFollows.map(EdgeTypeTraverse.basic).map(_.reverse)),
        filters = filters) :: Nil
    }
  }

  def dto = NodeClauseDTO(predicate, variable, condition.map(_.dto))
}

case class NodeClauseDTO(predicate: NodePredicate, variable: String, condition: Option[ConditionDTO]) {
  def toModel = NodeClause(predicate, variable, condition.map(_.toModel))
}

object NodeClauseDTO {
  implicit val format = Json.format[NodeClauseDTO]
}

case class EdgeClause(predicate: EdgePredicate, from: String, to: String, condition: Option[Condition], modifier: Option[BooleanModifier]) extends SrcLogClause {
  def contains(ids: Set[String]) = {
    ids.contains(from) || ids.contains(to)
  }

  def implicitNodes = {
    val f = withDefined(predicate.fromImplicit) { f =>
      NodeClause(f, from, None) :: Nil
    }

    val t = withDefined(predicate.toImplicit) { t =>
      NodeClause(t, to, withFlag(!predicate.suppressNodeCheck) {
        condition
      }) :: Nil
    }

    f ++ t
  }

  def dto = EdgeClauseDTO(predicate, from, to, condition.map(_.dto), modifier)
}

case class EdgeClauseDTO(predicate: EdgePredicate, from: String, to: String, condition: Option[ConditionDTO], modifier: Option[BooleanModifier]) {
  def toModel = EdgeClause(predicate, from, to, condition.map(_.toModel), modifier)
}

object EdgeClauseDTO {
  implicit val format = Json.format[EdgeClauseDTO]
}
