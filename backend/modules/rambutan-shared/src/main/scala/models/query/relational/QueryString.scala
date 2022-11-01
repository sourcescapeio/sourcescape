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

  def stringify(item: RelationalQuery) = {
    val ordering = item.forceOrdering match {
      case Some(o) => "%ordering(" + o.mkString(",") + ")"
      case _       => ""
    }

    val select = "SELECT " + (item.select match {
      case RelationalSelect.SelectAll => "*"
      // case RelationalSelect.CountAll       => "COUNT(*)"
      case RelationalSelect.Select(c) => c.mkString(",")
      // case RelationalSelect.Distinct(c, _) => s"DISTINCT ${c.mkString(",")}"
      // case RelationalSelect.GroupedCount(g, t, c) => {
      //   s"GROUPED_COUNT_BY(${g}.${t.identifier}, ${c.mkString(", ")})"
      // }
    })

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
      offset,
      limit).mkString("\n")
  }

  def stringifyGraphQuery(query: GraphQuery): String = {
    (stringifyGraphRoot(query.root) :: query.traverses.map(t => stringifyTraverse(t, 0))).mkString("")
  }

  private def stringifyTraceQuery(query: TraceQuery) = {
    (stringifyFromRoot(query.from) :: query.traverses.map(t => stringifyTraverse(t, 0))).mkString("")
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

    item match {
      case LinearTraverse(follows) => {
        ".linear_traverse [\n" + follows.map(i => stringifyFollow(i, tabs + 1)).mkString(",\n") + "\n]"
      }
      case NodeCheck(filters) => {
        ".node_check {\n" + filters.map(i => stringifyNodeFilter(i, tabs + 1)).mkString(",\n") + "\n}"
      }
      case RepeatedLinearTraverse(follows, repeated) => {
        ".repeated_traverse {\n" +
          s"${spaces}follow: [" + follows.map(i => stringifyFollow(i, tabs + 1)).mkString(",\n") + "\n]" +
          s"${spaces}repeat: [" + repeated.map(i => stringifyFollow(i, tabs + 1)).mkString(",\n") + "\n]"
      }
      case RepeatedEdgeTraverse(_, _) => {
        "!not_supported!"
      }
    }
  }

  private def stringifyFollow(item: EdgeFollow, tabs: Int) = {
    val spaces = " " * (2 * tabs)
    if (item.traverses.exists(_.filter.isDefined)) {
      s"${spaces}${item.followType.identifier}[\n" + item.traverses.map(i => stringifyFollowTraverse(i, tabs + 1)).mkString(",\n") + "\n]"
    } else {
      s"${spaces}${item.followType.identifier}[" + item.traverses.map(i => stringifyFollowTraverse(i, 0)).mkString(",") + "]"
    }
  }

  private def stringifyFollowTraverse(item: EdgeTypeTraverse, tabs: Int) = {
    val spaces = " " * (2 * tabs)
    item.filter match {
      case Some(f) => {
        s"${spaces}{\n" + s"${spaces}${spaces}type : " + "\"" + stringifyKeyword(item.edgeType.identifier) + "\"\n" + stringifyEdgeFilter(f, tabs + 1) + "}\n"
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
  private def stringifyKeyword(item: String) = "\"" + item + "\""

  private def indent(tabs: Int) = " " * (2 * tabs)

}
