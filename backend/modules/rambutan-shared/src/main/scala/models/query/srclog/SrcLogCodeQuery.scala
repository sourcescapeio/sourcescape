package models.query

import silvousplay.imports._
import models.IndexType
import fastparse._
import MultiLineWhitespace._
import play.api.libs.json._

private object Base26 {
  def intToId(id: Int): String = {
    val next = ((id % 26) + 65).toChar.toString

    id / 26 match {
      case 0     => next
      case recur => intToId(recur) + next
    }
  }

  def idToInt(id: String, power: Int): Int = {
    id.lastOption match {
      case Some(item) => {
        val curr = (((item - 65) % 26) + 1) * scala.math.pow(26, power).toInt
        idToInt(id.dropRight(1), power + 1) + curr
      }
      case None => 0
    }
  }
}

case class SrcLogCodeQuery(
  language: IndexType,
  nodes:    List[NodeClause],
  edges:    List[EdgeClause],
  aliases:  Map[String, String],
  root:     Option[String],
  selected: List[RelationalSelect]) extends SrcLogQuery {

  def subset(sub: Set[String]) = {
    SrcLogCodeQuery(
      language,
      nodes.filter(n => sub.contains(n.variable)),
      edges.filter(_.contains(sub)),
      aliases.filter {
        case (k, v) => sub.contains(k)
      },
      root.filter(sub.contains),
      Nil)
  }

  def sortedNodes = nodes.sortBy(_.variable).map(_.dto)
  def sortedEdges = edges.sortBy(e => s"${e.from}:${e.to}").map(_.dto)

  def dto = SrcLogCodeQueryDTO(
    language,
    sortedNodes,
    sortedEdges,
    aliases,
    root)
}

case class SrcLogCodeQueryDTO(
  language: IndexType,
  nodes:    List[NodeClauseDTO],
  edges:    List[EdgeClauseDTO],
  aliases:  Map[String, String],
  root:     Option[String]) { //more like forceRoot
  def toModel = SrcLogCodeQuery(
    language,
    nodes.map(_.toModel),
    edges.map(_.toModel),
    aliases,
    root,
    Nil)
}

object SrcLogCodeQueryDTO {
  implicit val format = Json.format[SrcLogCodeQueryDTO]
}

object SrcLogCodeQuery {
  {}

  private def indexType[_: P] = P(Lexical.keywordChars).map { str =>
    IndexType.withNameUnsafe(str.trim)
  }
  private def aliasDirective[_: P] = P("%alias(" ~ SrcLogQuery.varChars ~ "=" ~ Lexical.keywordChars ~ ")" ~ ".")
  private def rootDirective[_: P] = P("%root(" ~ SrcLogQuery.varChars ~ ")" ~ ".")
  private def selectDirective[_: P] = P("%SELECT(" ~/ RelationalQuery.Select.stanza ~ ").")

  //  SrcLogQuery.varChars ~ ("," ~ SrcLogQuery.varChars).rep(0) ~ ")" ~ ".") map {
  //   case (a, bs) => a :: bs.toList
  // }

  private def query[_: P](indexType: IndexType) = {
    for {
      clauses <- P(Start ~ SrcLogQuery.clause(indexType.nodePredicate, indexType.edgePredicate).rep(1))
      finalDirectives <- P(aliasDirective.rep(0) ~ rootDirective.? ~ selectDirective.? ~ End)
    } yield {
      val (aliasDirectives, rootDirective, selectDirective) = finalDirectives

      val nodes = clauses.flatMap {
        case n @ NodeClause(_, _, _) => Some(n)
        case _                       => None
      }

      val edges = clauses.flatMap {
        case e @ EdgeClause(_, _, _, _, _) => Some(e)
        case _                             => None
      }

      val aliases = aliasDirectives.toMap

      SrcLogCodeQuery(indexType, nodes.toList, edges.toList, aliases, rootDirective, selectDirective.getOrElse(Nil))
    }
  }

  def parseOrDie(q: String, indexType: IndexType): SrcLogCodeQuery = {
    fastparse.parse(q, query(indexType)(_)) match {
      case fastparse.Parsed.Success(query, _) => query
      case f: fastparse.Parsed.Failure => {
        throw models.Errors.badRequest("query.parse", f.trace().toString)
      }
    }
  }
}
