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

object GraphQuery2 {
  object Base {
    // private def keywordChars[_: P] = P(CharIn("0-9a-zA-Z_").rep(1).!)

    def quotedKeyword[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P("\"" ~/ Lexical.keywordChars ~ "\"")
    }

    private def keywordList[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("[" ~/ Base.quotedKeyword ~ ("," ~ Base.quotedKeyword).rep(1) ~ "]").map {
        case (first, rest) => first :: rest.toList
      }
    }

    def keywordArg[_: P] = P(quotedKeyword).map(id => id :: Nil) | keywordList

    //
    def quotedChars[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P("\"" ~/ Lexical.quotedChars ~ "\"")
    }

    private def charsList[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("[" ~/ Base.quotedChars ~ ("," ~ Base.quotedChars).rep(1) ~ "]").map {
        case (first, rest) => first :: rest.toList
      }
    }

    def charsArg[_: P] = P(quotedChars).map(id => id :: Nil) | charsList

    private def numList[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("[" ~/ Lexical.numChars ~ ("," ~ Lexical.numChars).rep(1) ~ "]").map {
        case (first, rest) => first.toInt :: rest.toList.map(_.toInt)
      }
    }

    def numsArg[_: P] = P(Lexical.numChars).map(id => id.toInt :: Nil) | numList
  }

  object NodeId {
    private def nodeIdFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("id" ~ ":" ~/ Base.keywordArg) map (ids => NodeIdsFilter(ids))
    }

    def targetSetting[_: P] = P(nodeIdFilter) map (_ :: Nil)
  }

  object NodeType {
    // Type
    case class QueryNodeType(val identifier: String) extends models.index.NodeType
    private def nodeType[_: P] = P(Base.quotedKeyword).map { str =>
      QueryNodeType(str.trim)
    }

    private def nodeTypeList[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("[" ~/ nodeType ~ ("," ~ nodeType).rep(1) ~ "]").map {
        case (first, rest) => first :: rest.toList
      }
    }

    private def nodeTypeArg[_: P] = P(nodeType).map(nt => nt :: Nil) | nodeTypeList

    private def nodeProp[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P(Base.quotedKeyword ~ ":" ~ Base.quotedKeyword) map {
        case (k, v) => GenericGraphProperty(k, v)
      }
    }

    // Type
    private def nodeTypeFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("type" ~ ":" ~/ nodeTypeArg) map (typ => NodeTypesFilter(typ))
    }

    // Names
    private def nodeNameFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("name" ~ ":" ~/ Base.charsArg) map (names => NodeNamesFilter(names))
    }

    // Index
    private def nodeIndexFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("index" ~ ":" ~/ Base.numsArg) map (idx => NodeIndexesFilter(idx))
    }

    // Props
    private def nodePropsFilter[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace

      // ~ ("," ~ nodeProp).rep(0)
      P("props" ~ ":" ~/ "{" ~/ nodeProp ~ "}") map {
        case head => NodePropsFilter(head :: Nil)
        // case (head, rest) => NodePropsFilter(head :: rest.toList)
      }
    }

    def targetSetting[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P(nodeTypeFilter ~ ("," ~ (nodeNameFilter | nodeIndexFilter | nodePropsFilter)).?) map {
        case (a, b) => a :: b.toList
      }
    }
  }

  object Traverse {
    private def reverseSetting[_: P] = P("reverse") map (_ => true)

    private def edgeType[_: P] = {
      P(Base.quotedChars).map { str =>
        val trimmed = str.trim
        trimmed.split("::") match {
          case Array(v) => GenericGraphEdgeType.withNameUnsafe(trimmed)
          case Array(idx, v) => IndexType.withNameUnsafe(idx) match {
            case IndexType.Javascript => JavascriptGraphEdgeType.withNameUnsafe(trimmed)
            case IndexType.Ruby       => RubyGraphEdgeType.withNameUnsafe(trimmed)
          }
        }
      }
    }

    private def basicEdgeType[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit

      P(edgeType ~ ("." ~/ reverseSetting).?).map {
        case (edgeT, Some(_)) => edgeT.opposite
        case (edgeT, _)       => edgeT
      }.map { item =>
        EdgeTypeTraverse(item, None)
      }
    }

    // edge type filter

    private def edgeIndexFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("index" ~ ":" ~/ Base.numsArg) map (idx => EdgeIndexesFilter(idx.map(_.toInt)))
    }
    private def edgeNameFilter[_: P] = {
      implicit val whitespace = SingleLineWhitespace.whitespace
      P("name" ~ ":" ~/ Base.charsArg) map (names => EdgeNamesFilter(names))
    }

    private def edgeFilter[_: P] = P(edgeIndexFilter | edgeNameFilter)

    private def expandedEdgeType[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("{" ~/ "type" ~ ":" ~ basicEdgeType ~ ("," ~ edgeFilter).? ~ "}").map {
        case (ty, filt) => ty.copy(filter = filt)
      }
    }

    private def edgeTypeStanza[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P(expandedEdgeType | basicEdgeType)
    }

    // *
    private def follow[_: P](followType: FollowType) = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P(s"${followType.identifier}[" ~/ edgeTypeStanza ~ ("," ~ edgeTypeStanza).rep(0) ~ "]").map {
        case (head, rest) => {
          val all = head :: rest.toList
          EdgeFollow(all, followType)
        }
      }
    }

    private def linearFollow[_: P] = P(follow(FollowType.Optional) | follow(FollowType.Star) | follow(FollowType.Target))

    private def linearTraverse[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("linear_traverse" ~ "[" ~/ linearFollow ~ ("," ~ linearFollow).rep(0) ~ "]") map {
        case (head, rest) => {
          val follows = (head :: rest.toList)
          val finalTarget = follows.last match {
            case v if v.followType =?= FollowType.Target => v
            case _                                       => throw new Exception("final follow in traverse is not a target")
          }
          LinearTraverse(
            follows.dropRight(1),
            finalTarget)
        }
      }
    }

    def traverses[_: P] = P(linearTraverse)
  }

  private def root[_: P] = {
    // implicit val whitespace = MultiLineWhitespace.whitespace
    P("root" ~ "{" ~/ (NodeId.targetSetting | NodeType.targetSetting) ~ "}") map {
      case nodeFilters => GraphRoot(nodeFilters)
    }
  }

  def query[_: P] = {
    P(root ~ ("." ~ Traverse.traverses).rep(0)) map {
      case (root, traverses) => GraphQuery(root, traverses.toList)
    }
  }

  // should we be able to specify targeting in graph query?
  private def fileSetting[_: P] = P("file=\"" ~/ Lexical.quotedChars ~ "\"")

  private def targeting[_: P] = P("%targeting(" ~ fileSetting ~ ")").map {
    case file => QueryTargetingRequest.AllLatest(Some(file))
  }

  def fullQuery[_: P] = P(Start ~ targeting.? ~ query ~ End)

  def parseOrDie(q: String): (Option[QueryTargetingRequest], GraphQuery) = {
    fastparse.parse(q, fullQuery(_)) match {
      case fastparse.Parsed.Success((maybeTargeting, query), _) => {
        (maybeTargeting, query)
      }
      case f: fastparse.Parsed.Failure => {
        println(f)
        throw new Exception("Invalid query")
      }
    }
  }
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

  private def repeatedTraverse[_: P] = P("repeated{" ~/ edgeTraverse ~ ("," ~ edgeTraverse).rep(0) ~ "}").map {
    case (first, rest) => RepeatedEdgeTraverseNew(first :: rest.toList)
  }

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
  private def nodeNamesFilter[_: P] = P("names={\"" ~/ ("\"" ~ Lexical.quotedChars ~ "\"") ~ ("," ~ "\"" ~ Lexical.quotedChars ~ "\"").rep(0) ~ "}") map {
    case (head, rest) => NodeMultiNameFilter(head :: rest.toList)
  }
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
  private def nodeTypeTargetSetting[_: P] = P(nodeTypeFilter ~ ("," ~ (nodeIndexFilter | nodeNamesFilter | nodeNameFilter | nodePropsFilter)).?) map {
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

  /**
   * Subqueries
   */
  private def traverseSetting[_: P]: P[List[Traverse]] = P("traverses={" ~ traverse ~ ("." ~ traverse).rep(0) ~ "}") map {
    case (head, rest) => head :: rest.toList
  }
  private def reverseTraverse[_: P]: P[ReverseTraverse] = P("reverse{" ~/ (followSetting ~ ",").? ~ traverseSetting ~ "}") map {
    case (follow, traverses) => ReverseTraverse(follow.getOrElse(EdgeTypeFollow.empty), traverses)
  }

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

  def traverse[_: P] = P(edgeTraverse | nodeTraverse | onehopTraverse | onehopReverse | reverseTraverse | repeatedTraverse)

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
