package models.query

import silvousplay.imports._
import play.api.libs.json._
import fastparse._
import MultiLineWhitespace._
import models.graph.GenericGraphProperty
import models.index.ruby.OrNode

trait SrcLogQuery {

  // inherit
  val nodes: List[NodeClause]
  val edges: List[EdgeClause]
  val root: Option[String]
  val selected: List[RelationalSelect]

  lazy val vertexes: Set[String] = {
    (nodes.map(_.variable) ++ edges.flatMap {
      case e @ EdgeClause(_, from, to, _, _) => {
        from :: to :: Nil
      }
    }).toSet
  }

  lazy val edgeMap: Map[String, List[String]] = {
    edges.flatMap {
      case EdgeClause(_, from, to, _, _) => {
        (from, to) :: (to, from) :: Nil
      }
    }.groupBy(_._1).map {
      case (k, v) => k -> v.map(_._2)
    }
  }

  // We do assume all variable ids are the same in NodeClause
  private def findMostSpecific(variable: String, nodes: List[NodeClause]): NodeClause = {
    def nextSpecific(a: NodeClause, b: NodeClause) = {
      // strongest condition
      val bestCondition = (a.condition, b.condition) match {
        case (Some(ac), Some(bc)) => {
          if (ac =?= bc) {
            Some(bc)
          } else {
            throw new Exception(s"condition conflict ${a.condition} ${b.condition}")
          }
        }
        case (Some(ac), None) => Some(ac)
        case (None, Some(bc)) => Some(bc)
        case _                => None
      }

      val bestPredicate = (a.predicate, b.predicate) match {
        case (aPred: SimpleNodePredicate, bPred: SimpleNodePredicate) => {
          if (aPred.nodeType =?= bPred.nodeType) {
            aPred // and condition
          } else {
            throw new Exception(s"predicate conflict ${aPred} ${bPred}")
          }
        }
        case (aPred: SimpleNodePredicate, bPred: OrNodePredicate) => {
          if (bPred.in.contains(a.predicate)) {
            aPred
          } else {
            throw new Exception(s"predicate conflict ${aPred} ${bPred}")
          }
        }
        case (aPred: OrNodePredicate, bPred: SimpleNodePredicate) => {
          if (aPred.in.contains(bPred)) {
            bPred
          } else {
            throw new Exception(s"predicate conflict ${aPred} ${bPred}")
          }
        }
        case (aPred: OrNodePredicate, bPred: OrNodePredicate) => {
          val inter = aPred.in.intersect(bPred.in)
          if (inter.length > 0) {
            OrNodePredicate(inter)
          } else {
            throw new Exception(s"predicate conflict ${aPred} ${bPred}")
          }
        }
        case _ => throw new Exception("unimplemented")
      }

      NodeClause(bestPredicate, variable, bestCondition)
    }

    nodes match {
      case Nil         => throw new Exception("should never happen. empty nodes list.")
      case head :: Nil => head
      case head :: rest => {
        rest.foldLeft(head) {
          case (acc, next) => nextSpecific(acc, next)
        }
      }
    }
  }

  lazy val allNodes = {
    (nodes ++ edges.flatMap(_.implicitNodes)).groupBy(_.variable).map {
      case (k, vs) => findMostSpecific(k, vs)
    }.toList
  }

  lazy val nodeMap = allNodes.map {
    case n => n.variable -> n
  }.toMap // unique by variable

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
  private def namesCondition[_: P] = P("names" ~ "={" ~ ("\"" ~ quotedChars ~ "\"") ~ ("," ~ "\"" ~ quotedChars ~ "\"").rep(0) ~ "}") map {
    case (head, rest) => MultiNameCondition(head :: rest.toList)
  }
  private def conditionsBlock[_: P] = P("[" ~/ (indexCondition | nameCondition | namesCondition) ~ "]")

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
        EdgeClause(edgePredicate.withNameUnsafe(pred), from, to, cond, mod)
      }
    }
  }

  // expose this
  def clause[_: P](p: Plenumeration[_ <: NodePredicate], e: Plenumeration[_ <: EdgePredicate]) = {
    P(nodeClause(p) | edgeClause(e))
  }

}
