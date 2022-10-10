package models.query

import models.{ IndexType, ESQuery }
import models.index.esprima._
import silvousplay.imports._
import fastparse._
import MultiLineWhitespace._
import play.api.libs.json._
import models.graph.GenericGraphProperty

sealed case class GraphRoot(filters: List[NodeFilter]) {
  def query = filters.map(_.query)
}

case class GraphQuery(root: GraphRoot, traverses: List[Traverse]) {
  def addTraverse(extra: List[Traverse]) = this.copy(traverses = traverses ++ extra)
}

object GraphQuery {
  private def edgeTypeInner[_: P] = P(Lexical.keywordChars).map { str =>
    val trimmed = str.trim
    trimmed.split("::") match {
      case Array(v) => GenericGraphEdgeType.withNameUnsafe(trimmed)
      case Array(idx, v) => IndexType.withNameUnsafe(idx) match {
        case IndexType.Javascript => JavascriptGraphEdgeType.withNameUnsafe(trimmed)
        case IndexType.Ruby       => RubyGraphEdgeType.withNameUnsafe(trimmed)
      }
    }
  }
  private def edgeType[_: P] = P(edgeTypeInner ~ ("." ~ reverseSetting).?) map {
    case (edgeT, Some(_)) => edgeT.opposite
    case (edgeT, _)       => edgeT
  }

  /**
   * Edges
   */
  private def edgeIndexFilter[_: P] = P("index=" ~/ Lexical.numChars) map (idx => EdgeIndexFilter(idx.toInt))
  private def edgeNameFilter[_: P] = P("name=\"" ~/ Lexical.quotedChars ~ "\"") map (name => EdgeNameFilter(name.trim))
  private def edgePropsFilter[_: P] = P("props=(" ~/ nodeProp ~ ("," ~ nodeProp).rep(0) ~ ")") map {
    case (head, rest) => EdgePropsFilter(head :: rest.toList)
  }

  private def edgeFilter[_: P] = P(edgeIndexFilter | edgeNameFilter | edgePropsFilter)

  private def reverseSetting[_: P] = P("reverse") map (_ => true)
  private def edgeTypeStanza[_: P] = P(edgeType ~ ("[" ~ edgeFilter ~ "]").?) map {
    case (edgeT, edgeFilter) => EdgeTypeTraverse(edgeT, edgeFilter)
  }

  private def edgeTypeTargetType[_: P] = P("edge_types:(" ~/ edgeTypeStanza ~ ("," ~ edgeTypeStanza).rep(0) ~ ")") map {
    case (item, rest) => EdgeTypeTarget(item :: rest.toList)
  }
  private def noneTargetType[_: P] = P("none") map (_ => EdgeTypeTarget.empty)
  private def targetType[_: P] = P(edgeTypeTargetType | noneTargetType)

  private def edgeTypeFollowType[_: P] = P("edge_types:(" ~/ edgeTypeStanza ~ ("," ~ edgeTypeStanza).rep(0) ~ ")") map {
    case (head, rest) => EdgeTypeFollow(head :: rest.toList)
  }
  private def followType[_: P] = P(edgeTypeFollowType)

  private def followSetting[_: P] = P("follow=" ~/ followType)
  private def targetSetting[_: P] = P("target=" ~ targetType)

  private def trueSetting[_: P] = P("true") map (_ => true)
  private def falseSetting[_: P] = P("false") map (_ => false)
  private def typeHintSetting[_: P] = P("type_hint=" ~/ nodeType)

  private def edgeTraverse[_: P] = P("traverse[" ~/ (followSetting ~ ",").? ~ targetSetting ~ ("," ~ typeHintSetting).? ~ "]") map {
    case (follow, target, typeHint) => EdgeTraverse(
      follow.getOrElse(EdgeTypeFollow.empty),
      target,
      typeHint)
  }

  private def onehopTraverse[_: P] = P("one_hop[" ~/ followSetting.? ~ "]") map {
    case follow => OneHopTraverse(
      follow.map(_.traverses).getOrElse(EdgeTypeFollow.all))
  }

  private def onehopReverse[_: P] = P("one_hop_reverse[]") map {
    case follow => OneHopTraverse(EdgeTypeFollow.all.map(_.reverse))
  }

  // private def repeatedTraverse[_:P] = P("traverse_repeated[" ~/ (followSetting ~ ",").? ~ targetSetting ~ ("," ~ followExportsSEtting).? ~ "]") map {
  //   case (follow, target, followExports) => EdgeTraverseRepeated(
  //     follow.getOrElse(EdgeTypeFollow.empty),
  //     target,
  //     followExports.getOrElse(false))
  // }

  /**
   * Nodes
   */
  case class TempNodeType(val identifier: String) extends models.index.NodeType
  private def nodeType[_: P] = P(Lexical.keywordChars).map { str =>
    TempNodeType(str.trim)
  }

  private def nodeTypeFilter[_: P] = P("type=" ~/ nodeType) map (typ => NodeTypeFilter(typ))
  private def nodeNotTypesFilter[_: P] = P("not_types=(" ~/ nodeType ~ ("," ~ nodeType).rep(0) ~ ")") map {
    case (head, rest) => NodeNotTypesFilter(head :: rest.toList)
  }
  private def nodeNameFilter[_: P] = P("name=\"" ~/ Lexical.quotedChars ~ "\"") map (name => NodeNameFilter(name.trim))
  private def nodeIndexFilter[_: P] = P("index=\"" ~/ Lexical.numChars ~ "\"") map (idx => NodeIndexFilter(idx.toInt))

  private def nodeProp[_: P] = P(Lexical.keywordChars ~ "=\"" ~ Lexical.quotedChars ~ "\"") map {
    case (k, v) => GenericGraphProperty(k, v)
  }
  private def nodePropsFilter[_: P] = P("props=(" ~/ nodeProp ~ ("," ~ nodeProp).rep(0) ~ ")") map {
    case (head, rest) => NodePropsFilter(head :: rest.toList)
  }

  private def nodeIdFilter[_: P] = P("id=" ~/ Lexical.keywordChars) map (id => NodeIdFilter(id.trim))
  // private def nodeFilter[_: P] = P(nodeTypeFilter | nodeNameFilter | nodeIdFilter)

  private def nodeIdTargetSetting[_: P] = P(nodeIdFilter) map (_ :: Nil)
  private def nodeNotTypesTargetSetting[_: P] = P(nodeNotTypesFilter) map (_ :: Nil)
  private def nodeTypeTargetSetting[_: P] = P(nodeTypeFilter ~ ("," ~ (nodeIndexFilter | nodeNameFilter | nodePropsFilter)).?) map {
    case (a, b) => a :: b.toList
  }
  private def nodeTargetSetting[_: P] = P("target=(" ~ (nodeIdTargetSetting | nodeNotTypesTargetSetting | nodeTypeTargetSetting) ~ ")")

  private def nodeTraverse[_: P] = P("node_traverse[" ~/ (followSetting ~ ",").? ~ nodeTargetSetting ~ "]") map {
    case (follow, target) => NodeTraverse(follow.getOrElse(EdgeTypeFollow.empty), target)
  }

  /**
   * Exports
   */
  private def edgeTypeList[_: P] = P("[" ~ edgeType ~ ("," ~ edgeType).rep(0) ~ "]") map {
    case (head, tail) => head :: tail.toList
  }
  private def emptyEdgeTypeList[_: P] = P("[" ~ "]") map (_ => List.empty[GraphEdgeType])
  private def statefulMappingTuple[_: P] = P(edgeType ~ "->" ~ (edgeTypeList | emptyEdgeTypeList))
  private def statefulMapping[_: P] = P("mapping={" ~/ statefulMappingTuple ~ ("," ~ statefulMappingTuple).rep(0) ~ "}") map {
    case (headA, headB, tail) => ((headA, headB) :: tail.toList).map {
      case (k, v) => k -> v.toList
    }.toMap
  }
  // private def statefulFollowSetting[_: P] = P("follow=" ~/ (edgeTypeList | emptyEdgeTypeList))
  // private def statefulTargetSetting[_: P] = P("target=" ~/ edgeTypeList)
  // private def teleportFrom[_: P] = P("teleport_from=" ~/ nodeType)
  // private def teleportTo[_: P] = P("teleport_to=" ~/ nodeType)
  // private def statefulTraverse[_: P] = P("stateful_traverse[" ~ teleportFrom ~ "," ~ teleportTo ~ "," ~ statefulMapping ~ "," ~ statefulFollowSetting ~ "," ~ statefulTargetSetting ~ "]") map {
  //   case (from, to, mapping, follow, target) => StatefulTraverse(from, to, mapping, follow.toList, target.toList)
  // }

  /**
   * Subqueries
   */
  // private def subquerySetting[_: P]: P[GraphQuery] = P("subquery={" ~ query ~ "}")

  // private def subqueryTraverse[_: P] = P("traverse_subquery[" ~/ (followSetting ~ ",").? ~ subquerySetting ~ "]").map {
  //   case (follow, subquery) => {
  //     SubqueryTraverse(follow.getOrElse(EdgeTypeFollow.empty), subquery)
  //   }
  // }

  private def traverseSetting[_: P]: P[List[Traverse]] = P("traverses={" ~ traverse ~ ("." ~ traverse).rep(0) ~ "}") map {
    case (head, rest) => head :: rest.toList
  }
  private def reverseTraverse[_: P]: P[ReverseTraverse] = P("reverse[" ~/ (followSetting ~ ",").? ~ traverseSetting ~ "]") map {
    case (follow, traverses) => ReverseTraverse(follow.getOrElse(EdgeTypeFollow.empty), traverses)
  }

  /**
   * Filters
   */
  private def filterTraverse[_: P]: P[FilterTraverse] = P("filter" ~ "{" ~/ traverse ~ ("." ~ traverse).rep(0) ~ "}") map {
    case (head, rest) => FilterTraverse(head :: rest.toList)
  }

  // private def filterNotTraverse[_: P]: P[FilterNotTraverse] = P("filter_not" ~/ "{" ~/ traverse ~ ("." ~ traverse).rep(0) ~ "}") map {
  //   case (head, rest) => FilterNotTraverse(head :: rest.toList)
  // }

  /**
   * Targeting
   */
  private def repoSetting[_: P] = P("repo=\"" ~/ Lexical.quotedChars ~ "\"")

  private def fileSetting[_: P] = P("file=\"" ~/ Lexical.quotedChars ~ "\"")

  private def indexType[_: P] = P(Lexical.keywordChars).map { str =>
    IndexType.withNameUnsafe(str.trim)
  }
  private def indexTypeFilter[_: P] = P("index=" ~/ indexType)

  /**
   * Global
   */
  private def root[_: P] = P("root[" ~/ (nodeIdTargetSetting | nodeTypeTargetSetting) ~ "]") map {
    case nodeFilters => GraphRoot(nodeFilters)
  }

  def traverse[_: P] = P(edgeTraverse | nodeTraverse | onehopTraverse | onehopReverse | filterTraverse | reverseTraverse) // cannot do statefulTraverse

  private def targeting[_: P] = P("%targeting(" ~ fileSetting ~ ")").map {
    case file => QueryTargetingRequest.AllLatest(Some(file))
  }

  def fullQuery[_: P] = P(Start ~ targeting.? ~ query ~ End)

  def query[_: P] = P(root ~ ("." ~ traverse).rep(0)) map {
    case (root, traverses) => {
      GraphQuery(root, traverses.toList)
    }
  }
}
