package models.query

import silvousplay.imports._
import models.graph.GenericGraphProperty

object QueryString {
  // TODO: wtf is this?
  def stringifyScroll(item: QueryScroll) = {
    item.lastKey match {
      case Some(rk) => {
        val guts = rk.leaves.map {
          case (k, v) => s"  ${k}:[${v.key},${v.path},${v.id}]"
        }.mkString(",\n")
        s"%scroll(\n${guts}\n)"
      }
      case _ => ""
    }
  }

  def stringifySelect(select: RelationalSelect): String = {
    select match {
      case RelationalSelect.Column(c)          => c
      case RelationalSelect.Member(name, c, m) => c.id + "." + m.identifier + name.map(" AS " + _).getOrElse("")
      case RelationalSelect.Operation(name, op, members) => {
        op.identifier + "(" + members.map(stringifySelect).mkString(", ") + ")" + name.map(" AS " + _).getOrElse("")
      }
      case RelationalSelect.GroupedOperation(name, op, members) => {
        op.identifier + "(" + members.map(stringifySelect).mkString(", ") + ")" + name.map(" AS " + _).getOrElse("")
      }
    }
  }

  def stringify(item: RelationalQuery) = {
    val ordering = item.forceOrdering match {
      case Some(o) => "%ordering(" + o.mkString(",") + ")"
      case _       => ""
    }

    val select = "SELECT " + item.select.map(stringifySelect).mkString(", ")

    val from = "FROM " + stringifyGraphQuery(item.root.query) + " AS " + item.root.key

    val traces = item.traces.map { t =>
      "TRACE " + stringifyTraceQuery(t.query) + " AS " + t.key
    }.mkString("\n")

    val having = item.having.map { h =>
      "HAVING " + h._1 + " " + h._2.identifier
    }.mkString("\n")

    val intersect = item.intersect.map { i =>
      "INTERSECT " + i.mkString(", ")
    }.mkString("\n")

    val orderBy = item.orderBy match {
      case Nil => ""
      case some => some.map {
        case RelationalOrder(k, true) => s"${k} DESC"
        case RelationalOrder(k, _)    => k
      }.mkString(", ")
    }

    val offset = item.offset.map { i =>
      "OFFSET " + i
    }.getOrElse("")

    val limit = item.limit.map { l =>
      "LIMIT " + l
    }.getOrElse("")

    List(
      ordering,
      select,
      from,
      traces,
      having,
      intersect,
      orderBy,
      offset,
      limit).mkString("\n")
  }

  def stringifyGraphQuery(query: GraphQuery): String = {
    (stringifyGraphRoot(query.root) :: query.traverses.map(t => stringifyTraverse(t, 0))).mkString("")
  }

  private def stringifyTraceQuery(query: TraceQuery) = {
    (stringifyFromRoot(query.from) :: query.traverses.map(t => stringifyTraverse(t, 1))).mkString("\n")
  }

  private def stringifyFromRoot(item: FromRoot) = {
    item.leftJoin match {
      case false => "join[" + item.name + "]"
      case true  => "left_join[" + item.name + "]"
    }
  }

  private def stringifyGraphRoot(item: GraphRoot) = {
    // indent starts with 0
    val guts = item.filters.map(i => stringifyNodeFilter(i, tabs = 1)).mkString(",\n")

    "root{\n" + guts + "\n}"
  }

  private def stringifyNodeFilter(item: NodeFilter, tabs: Int) = {

    val indent = " " * (2 * tabs)

    item match {
      case NodeTypesFilter(inner)   => s"${indent}type : [" + stringifyKeywords(inner.map(_.identifier)) + "]"
      case NodeNamesFilter(name)    => s"${indent}name : [" + stringifyKeywords(name) + "]"
      case NodeIndexesFilter(index) => s"${indent}index : [" + index.mkString(",") + "]"
      case NodeIdsFilter(id)        => s"${indent}id : [" + stringifyKeywords(id) + "]"
      case NodePropsFilter(props) => s"${indent}props: {" + props.map {
        case GenericGraphProperty(k, v) => s"${indent}${indent}" + "\"" + k + "\"" + " : " + "\"" + v + "\""
      }.mkString(", ") + "\n}"
      case NodeAllFilter         => s"${indent}all"
      case NodeNotTypesFilter(_) => "NOT_IMPLEMENTED!!!"
    }
  }

  private def stringifyTraverse(item: Traverse, tabs: Int): String = {
    val spaces = " " * (2 * tabs)
    val spaces1 = " " * (2 * (tabs + 1))

    item match {
      case LinearTraverse(follows) => {
        val opener = s"${spaces}.linear_traverse [\n"
        val inner = follows.map(i => stringifyFollow(i, tabs + 1)).mkString(",\n")
        val closer = s"\n${spaces}]"
        opener + inner + closer
      }
      case NodeCheck(filters) => {
        s"${spaces}.node_check {\n" + filters.map(i => stringifyNodeFilter(i, tabs + 1)).mkString(",\n") + s"\n${spaces}}"
      }
      case RepeatedLinearTraverse(follows, repeated) => {
        val opener = s"${spaces}.repeated_traverse {\n"
        val inner = List(
          innerFollowArray("follow", follows, tabs + 1),
          innerFollowArray("repeat", repeated, tabs + 1)).mkString(",\n")
        val closer = s"\n${spaces}}"

        opener + inner + closer
      }
      case RepeatedEdgeTraverse(_, _) => {
        s"${spaces}!not_supported!"
      }
    }
  }

  private def innerFollowArray(name: String, follows: List[EdgeFollow], tabs: Int) = {
    val spaces = " " * (2 * tabs)

    s"${spaces}${name}: [\n" + follows.map(i => stringifyFollow(i, tabs + 1)).mkString(",\n") + s"\n${spaces}]"
  }

  private def stringifyFollow(item: EdgeFollow, tabs: Int) = {
    val spaces = " " * (2 * tabs)
    if (item.traverses.exists(_.filter.isDefined)) {
      s"${spaces}${item.followType.identifier}[\n" + item.traverses.map(i => stringifyFollowTraverse(i, tabs + 1)).mkString(",\n") + s"\n${spaces}]"
    } else {
      s"${spaces}${item.followType.identifier}[" + item.traverses.map(i => stringifyFollowTraverse(i, 0)).mkString(",") + "]"
    }
  }

  private def stringifyFollowTraverse(item: EdgeTypeTraverse, tabs: Int) = {
    val spaces = " " * (2 * tabs)
    val spaces2 = " " * (2 * (tabs + 1))
    item.filter match {
      case Some(f) => {
        val opener = s"${spaces}{\n"
        val typeF = s"${spaces2}type : " + stringifyKeyword(item.edgeType.identifier) + ",\n"
        val edgeF = stringifyEdgeFilter(f, tabs + 1)
        val closer = s"\n${spaces}}"
        opener + typeF + edgeF + closer
      }
      case _ => s"${spaces}${stringifyKeyword(item.edgeType.identifier)}"
    }
  }

  private def stringifyEdgeFilter(item: EdgeFilter, tabs: Int) = {
    val spaces = " " * (2 * tabs)

    item match {
      case EdgeNamesFilter(names)   => s"${spaces}name : [" + stringifyKeywords(names) + "]"
      case EdgeIndexesFilter(idxes) => s"${spaces}index : [" + idxes.mkString(", ") + "]"
    }
  }

  /**
   * Base helpers
   */
  private def stringifyKeywords(items: List[String]) = items.map(i => stringifyKeyword(i)).mkString(",")
  private def stringifyKeyword(item: String) = {
    item.endsWith(".reverse") match {
      case true  => "\"" + item.replaceFirst("\\.reverse$", "") + "\".reverse"
      case false => "\"" + item + "\""
    }
  }

  private def indent(tabs: Int) = " " * (2 * tabs)

}
