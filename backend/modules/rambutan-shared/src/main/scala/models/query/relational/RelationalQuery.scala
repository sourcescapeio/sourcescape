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

case class RelationalQuery(
  select:        List[RelationalSelect],
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

  def allKeys = root.key :: traces.map(_.key)

  def shouldOuterJoin(id: String) = {
    // IS NOT NULL >> should not outer join
    having.get(id) =/= Some(HavingFilter.IS_NOT_NULL)
  }

  def validate() = {
    val keySet = allKeys.toSet
    val columns = select.flatMap(_.columns)
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

  /**
   * SELECT
   *
   * @return
   */

  object Select {
    // column chars?
    private def columnChars[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P(CharIn("A-Za-z0-9_").rep(1).!) //
    }

    private def column[_: P] = columnChars.map {
      case t => RelationalSelect.Column(t)
    }.opaque("<column>")

    private def memberType[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P(StringIn("name", "id").!).map(i => MemberType.withNameUnsafe(i))
    }

    private def member[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P(column ~ "." ~/ memberType).map {
        case (c, m) => RelationalSelect.Member(name = None, c, m)
      }
    }

    private def memberField[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P(member ~ ("AS" ~/ columnChars).?).map {
        case (mem, name) => mem.copy(name = name)
      }.opaque("<member>")
    }

    private def operationType[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      // Unfortunately need to write these manually
      P(StringIn("COUNT", "CAT").!).map(i => OperationType.withNameUnsafe(i))
    }

    private def operationField[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P(member | operation)
    }

    private def operation[_: P]: P[RelationalSelect.Operation] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P(operationType ~ "(" ~ operationField ~ ("," ~ operationField).rep(0) ~ ")" ~ ("AS" ~/ columnChars).?).map {
        case (o, head, rest, n) => RelationalSelect.Operation(n, o, head :: rest.toList)
      }
    }

    private def selectField[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P(operation | memberField | column)
    }

    def setting[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P(selectField ~ ("," ~/ selectField).rep(0)).map {
        case (first, rest) => first :: rest.toList
      }
    }
  }

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
      "SELECT" ~/ Select.setting ~/
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
