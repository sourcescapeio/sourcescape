package models.query

import models.index.GraphNode
import silvousplay.TSort
import silvousplay.imports._
import models.Errors
import fastparse._
import MultiLineWhitespace._

case class KeyedQuery[T](key: String, query: T) {

  def assign(f: T => T) = this.copy(query = f(query))
}

abstract sealed class HavingFilter(val identifier: String) extends Identifiable

object HavingFilter extends Plenumeration[HavingFilter] {

  case object IS_NOT_NULL extends HavingFilter("IS_NOT_NULL")
  case object IS_NULL extends HavingFilter("IS_NULL")

}

sealed abstract class GroupingType(val identifier: String) extends Identifiable {
  val identifiers: List[String]
}

object GroupingType extends Plenumeration[GroupingType] {
  case object None extends GroupingType("none") {
    val identifiers = Nil
  }
  case object Repo extends GroupingType("repo") {
    val identifiers = List("repo")
  }
  case object RepoFile extends GroupingType("repo_file") {
    val identifiers = List("repo", "file")
  }
}

sealed abstract class RelationalSelect(val identifier: String) extends Identifiable {
  val isDiff: Boolean = false
}

object RelationalSelect {
  case class Distinct(columns: List[String], named: List[String]) extends RelationalSelect("distinct") {
    override val isDiff = false
  }

  case class GroupedCount(grip: String, grouping: GroupingType, columns: List[String]) extends RelationalSelect("grouped") {
    override val isDiff = true

    def displayKeys(in: Map[String, GraphTrace[GraphNode]]): List[(String, String)] = {
      val initial = grouping match {
        case GroupingType.None => {
          Nil
        }
        case GroupingType.Repo => {
          in.get(grip).map(i => "repo" -> i.terminusId.repo).toList
        }
        case GroupingType.RepoFile => {
          in.get(grip).toList.flatMap { i =>
            ("repo", i.terminusId.repo) :: ("file" -> i.terminusId.path) :: Nil
          }
        }
      }

      val keys = columns.flatMap { i =>
        in.get(i).map(ii => i -> ii.terminusId.name.getOrElse("NULL")) // ewww
      }

      (initial ++ keys)
    }
  }
  case object CountAll extends RelationalSelect("count") {
    override val isDiff = true
  }

  case object SelectAll extends RelationalSelect("star")
  case class Select(columns: List[String]) extends RelationalSelect("select")
}

case class RelationalQuery(
  select:        RelationalSelect,
  root:          KeyedQuery[GraphQuery],
  traces:        List[KeyedQuery[TraceQuery]],
  having:        Map[String, HavingFilter],
  havingOr:      Map[String, HavingFilter],
  intersect:     List[List[String]],
  offset:        Option[Int],
  limit:         Option[Int],
  forceOrdering: Option[List[String]]) {

  def applyOffsetLimit(offset: Option[Int], limit: Option[Int]) = {
    this.copy(
      offset = offset.orElse(this.offset),
      limit = limit.orElse(this.limit))
  }

  lazy val fromLookup = traces.map { trace =>
    trace.key -> trace.query.fromName
  }.toMap

  lazy val calculatedOrdering = {
    forceOrdering match {
      case Some(o) => o
      case None => {
        if (traces.isEmpty) {
          List(root.key)
        } else {
          TSort.topologicalSort(fromLookup).toList.reverse
        }
      }
    }
  }

  val isDiff = select.isDiff

  def allKeys = root.key :: traces.map(_.key)

  def shouldOuterJoin(id: String) = {
    // IS NOT NULL >> should not outer join
    having.get(id) =/= Some(HavingFilter.IS_NOT_NULL)
  }

  def validate = {
    val keySet = allKeys.toSet
    val columns = select match {
      case RelationalSelect.Select(c)             => c
      case RelationalSelect.GroupedCount(g, _, c) => g :: c
      case _                                      => Nil
    }
    columns.foreach { s =>
      if (!keySet.contains(s)) {
        throw Errors.badRequest("invalid.select", s"Invalid key ${s} in select")
      }
    }

    traces.foldLeft(Set(root.key)) {
      case (acc, trace) => {
        // check repeats
        val key = trace.key
        if (acc.contains(key)) {
          throw Errors.badRequest("key.repeat", s"Repeated key: ${key}")
        }

        // check key
        val name = trace.query.from.name
        if (acc.contains(name)) {
          acc + key
        } else {
          throw Errors.badRequest("from.dne", s"Invalid from: ${name}")
        }
      }
    }
  }

  // transforms
  def applyGrouping(grip: String, grouping: GroupingType, selected: List[String]) = {
    this.copy(
      limit = None,
      select = RelationalSelect.GroupedCount(grip, grouping, selected))
  }

  def applyDistinct(selected: List[String], named: List[String]) = {
    this.copy(
      limit = None,
      select = RelationalSelect.Distinct(selected, named))
  }

}

object RelationalQuery {

  private def isNotNull[_: P] = P("IS_NOT_NULL").map(_ => HavingFilter.IS_NOT_NULL)
  private def isNull[_: P] = P("IS_NULL").map(_ => HavingFilter.IS_NULL)
  private def havingStanza[_: P] = Lexical.keywordChars ~ (isNotNull | isNull)
  private def intersectStanza[_: P] = P(Lexical.keywordChars ~ ("," ~ Lexical.keywordChars).rep(1)) map {
    case (head, rest) => head :: rest.toList
  }

  // def
  private def keyedGraph[_: P] = (GraphQuery.query ~ "AS" ~ Lexical.keywordChars).map {
    case (q, k) => KeyedQuery(k, q)
  }

  private def keyedTrace[_: P] = (TraceQuery.query ~ "AS" ~ Lexical.keywordChars).map {
    case (q, k) => KeyedQuery(k, q)
  }

  private def groupingType[_: P] = P(Lexical.keywordChars).map { str =>
    GroupingType.withNameUnsafe(str.trim)
  }

  private def countStar[_: P] = P("COUNT(*)").map {
    case _ => RelationalSelect.CountAll
  }
  private def groupedCount[_: P] = P("GROUPED_COUNT_BY(" ~ Lexical.keywordChars ~ "." ~ groupingType ~ ("," ~ Lexical.keywordChars).rep(0) ~ ")").map {
    case (grip, grouping, columns) => RelationalSelect.GroupedCount(grip, grouping, columns.toList)
  }
  private def star[_: P] = P("*").map {
    case _ => RelationalSelect.SelectAll
  }

  private def columns[_: P] = (Lexical.keywordChars ~ ("," ~ Lexical.keywordChars).rep(0)).map {
    case (first, rest) => RelationalSelect.Select(first :: rest.toList)
  }

  private def select[_: P] = P(groupedCount | star | countStar | columns)

  // directives
  private def orderingDirective[_: P] = P("%ordering(" ~ Lexical.keywordChars ~ ("," ~ Lexical.quotedChars).rep(0) ~ ")").map {
    case (head, rest) => head :: rest.toList
  }
  private def scrollKey[_: P] = P("[" ~ Lexical.quotedChars ~ "," ~ Lexical.quotedChars ~ "," ~ Lexical.quotedChars ~ "]").map {
    case (key, path, id) => RelationalKeyItem(key, path, id)
  }
  private def traceKey[_: P] = P(Lexical.keywordChars ~ ":" ~ scrollKey)

  private def scrollDirective[_: P] = P("%scroll(" ~/ traceKey ~ ("," ~ traceKey).rep(0) ~ ")").map {
    case (headKey, head, traces) => RelationalKey(((headKey -> head) :: traces.toList).toMap)
  }

  def query[_: P] = P(
    Start ~/
      scrollDirective.? ~/
      orderingDirective.? ~/
      // maybe directives
      "SELECT " ~/ select ~/
      "FROM" ~/ keyedGraph ~/
      ("TRACE" ~/ keyedTrace).rep(0) ~/
      // these we can compress as a where?
      ("HAVING " ~/ havingStanza).rep(0) ~/
      ("HAVING_OR " ~/ havingStanza).rep(0) ~/
      ("INTERSECT" ~/ intersectStanza).rep(0) ~/
      //
      ("OFFSET" ~/ Lexical.numChars).? ~/
      ("LIMIT" ~/ Lexical.numChars).? ~/
      End).map {
      case (scrollKey, ordering, select, root, traces, having, havingOr, intersect, offset, limit) => {
        (
          scrollKey,
          RelationalQuery(
            select,
            root,
            traces.toList,
            having.toMap,
            havingOr.toMap,
            intersect.toList,
            offset.map(_.toInt),
            limit.map(_.toInt),
            ordering))
      }
    }

  def parseOrDie(q: String): RelationalQuery = {
    fastparse.parse(q, query(_)) match {
      case fastparse.Parsed.Success((_, query), _) => {
        query
      }
      case f: fastparse.Parsed.Failure => {
        throw models.Errors.badRequest("query.parse", f.trace().toString)
      }
    }
  }
}
