package models.query

import silvousplay.imports._
import play.api.libs.json._
import fastparse._
import MultiLineWhitespace._
import models.graph.GenericGraphProperty

trait SrcLogQuery {

  // inherit
  val nodes: List[NodeClause]
  val edges: List[EdgeClause]
  val root: Option[String]
  val selected: List[String]

  // strips out all edges pointing in wrong direction
  lazy val baseTraversableEdges: List[DirectedSrcLogEdge] = {
    //

    edges.flatMap {
      case e @ EdgeClause(p, from, to, c, Some(_)) => {
        DirectedSrcLogEdge.forward(e) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, _) if p.forceForwardDirection => {
        DirectedSrcLogEdge.forward(e) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) if p.singleDirection => {
        DirectedSrcLogEdge.reverse(e) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) => {
        DirectedSrcLogEdge.forward(e) :: DirectedSrcLogEdge.reverse(e) :: Nil
      }
    }
  }

  lazy val vertexes: Set[String] = {
    (nodes.map(_.variable) ++ edges.flatMap {
      case e @ EdgeClause(_, from, to, _, _) => {
        from :: to :: Nil
      }
    }).toSet
  }

  // lazy val leftJoinVertexes: Set[String] = {
  //   edges.flatMap {
  //     case e @ EdgeClause(_, _, to, _, Some(_)) => {
  //       to :: Nil
  //     }
  //     case _ => Nil
  //   }.toSet
  // }

  lazy val edgeMap: Map[String, List[String]] = {
    edges.flatMap {
      case EdgeClause(_, from, to, _, _) => {
        (from, to) :: (to, from) :: Nil
      }
    }.groupBy(_._1).map {
      case (k, v) => k -> v.map(_._2)
    }
  }

  lazy val allNodes = {
    (nodes ++ edges.flatMap(_.implicitNodes)).groupBy(_.variable).map {
      case (k, vs) => {
        // dedupe nodes
        if (vs.map(_.predicate).distinct.length > 1) {
          throw new Exception(s"invalid nodes. multiple predicate types: ${k} ${vs.map(_.predicate)}")
        }
        vs.maxBy { v =>
          v.condition match {
            case Some(_) => 1
            case _       => 0
          }
        } // assume predicate is unique
      }
    }.toList
  }

  def subset(sub: Set[String]): SrcLogQuery

}

object SrcLogQuery {
  // char types
  def varChars[_: P] = P(CharIn("A-Z").! ~ CharIn("A-Z").rep(0).!) map {
    case (a, b) => a + b
  }
  private def quotedC(c: Char) = c != '"'
  private def quotedChars[_: P] = P(CharsWhile(quotedC).rep.!)

  private def maybeVar[_: P] = P(varChars | empty)

  private def empty[_: P] = P("?").map(_ => Hashing.uuid())

  // these don't really do anything
  // private def negate[_: P] = P("!").map(_ => BooleanModifier.Negate)
  private def optional[_: P] = P("?").map(_ => BooleanModifier.Optional)
  private def booleanModifier[_: P] = P(optional)

  // TODO: either switch to properties entirely or split up parsing better.
  // Right now can't name a property index or name for Generic
  private def indexCondition[_: P] = P("index" ~ "=" ~ Lexical.numChars) map (idx => IndexCondition(idx.toInt))
  private def nameCondition[_: P] = P("name" ~ "=" ~ "\"" ~ quotedChars ~ "\"") map (name => NameCondition(name))
  private def conditionsBlock[_: P] = P("[" ~/ (indexCondition | nameCondition) ~ "]")

  private def propConditionsBlock[_: P] = P("{" ~ propCondition ~ ("," ~ propCondition).rep(0) ~ "}") map {
    case (head, rest) => GraphPropertyCondition(head :: rest.toList)
  }
  private def propCondition[_: P] = P(Lexical.keywordChars ~ "=" ~ "\"" ~ quotedChars ~ "\"") map {
    case (k, v) => GenericGraphProperty(k, v)
  }

  private def nodeClause[_: P](nodePredicate: Plenumeration[_ <: NodePredicate]) = {
    P(Lexical.keywordChars ~ "(" ~ varChars ~ ")" ~ (conditionsBlock | propConditionsBlock).? ~ ".") map {
      case (pred, variable, cond) => {
        val np = UniversalNodePredicate.withName(pred).getOrElse {
          nodePredicate.withNameUnsafe(pred)
        }
        NodeClause(np, variable, cond)
      }
    }
  }

  private def edgeClause[_: P](edgePredicate: Plenumeration[_ <: EdgePredicate]) = {
    P(Lexical.keywordChars ~ "(" ~ maybeVar ~ "," ~ maybeVar ~ ")" ~ (conditionsBlock | propConditionsBlock).? ~ booleanModifier.? ~ ".") map {
      case (pred, from, to, cond, mod) => {
        val ep = UniversalEdgePredicate.withName(pred).getOrElse {
          edgePredicate.withNameUnsafe(pred)
        }
        EdgeClause(ep, from, to, cond, mod)
      }
    }
  }

  // expose this
  def clause[_: P](p: Plenumeration[_ <: NodePredicate], e: Plenumeration[_ <: EdgePredicate]) = {
    P(nodeClause(p) | edgeClause(e))
  }

}
