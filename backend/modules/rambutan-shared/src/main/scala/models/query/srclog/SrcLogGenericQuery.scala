package models.query

import fastparse._
import MultiLineWhitespace._

case class SrcLogGenericQuery(
  nodes:    List[NodeClause],
  edges:    List[EdgeClause],
  root:     Option[String],
  selected: List[String]) extends SrcLogQuery {

  def subset(sub: Set[String]) = {
    SrcLogGenericQuery(
      nodes.filter(n => sub.contains(n.variable)),
      edges.filter(_.contains(sub)),
      root.filter(sub.contains),
      Nil)
  }
}

object SrcLogGenericQuery {
  def query[_: P] = {
    for {
      clauses <- P(Start ~ SrcLogQuery.clause(GenericGraphNodePredicate, GenericGraphEdgePredicate).rep(1))
      // finalDirectives <- P(rootDirective.? ~ End)
    } yield {

      val nodes = clauses.flatMap {
        case n @ NodeClause(_, _, _) => Some(n)
        case _                       => None
      }

      val edges = clauses.flatMap {
        case e @ EdgeClause(_, _, _, _, _) => Some(e)
        case _                             => None
      }

      SrcLogGenericQuery(nodes.toList, edges.toList, None, Nil)
    }
  }

  def parseOrDie(q: String): SrcLogGenericQuery = {
    fastparse.parse(q, query(_)) match {
      case fastparse.Parsed.Success(query, _) => {
        query
      }
      case f: fastparse.Parsed.Failure => {
        throw models.Errors.badRequest("query.parse", f.trace().toString)
      }
    }
  }
}
