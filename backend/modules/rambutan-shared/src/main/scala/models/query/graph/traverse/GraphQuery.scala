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
  object Base {
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
      P("props" ~ ":" ~/ "{" ~/ nodeProp ~ ("," ~ nodeProp).rep(0) ~ "}") map {
        case (head, rest) => NodePropsFilter(head :: rest.toList)
      }
    }

    def targetSetting[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P(nodeTypeFilter ~ ("," ~ (nodeNameFilter | nodeIndexFilter | nodePropsFilter)).?) map {
        case (a, b) => a :: b.toList
      }
    }
  }

  object NodeAll {
    def targetSetting[_: P] = {
      implicit val whitespace = NoWhitespace.noWhitespaceImplicit
      P("all") map { _ =>
        NodeAllFilter :: Nil
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

    private def linearFollowList[_: P] = P("[" ~ linearFollow ~ ("," ~ linearFollow).rep(0) ~ "]").map {
      case (head, rest) => head :: rest.toList
    }

    private def linearTraverse[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("linear_traverse" ~/ linearFollowList) map { follows =>
        LinearTraverse(
          follows)
      }
    }

    private def nodeCheck[_: P] = {
      // implicit val whitespace = MultiLineWhitespace.whitespace
      P("node_check" ~ "{" ~/ (NodeId.targetSetting | NodeType.targetSetting | NodeAll.targetSetting) ~ "}") map {
        case nodeFilters => NodeCheck(nodeFilters)
      }
    }

    private def repeatedTraverse[_: P] = {
      P("repeated_traverse" ~ "{" ~/ "follow" ~ ":" ~ linearFollowList ~ "," ~ "repeat" ~ ":" ~ linearFollowList ~ "}") map {
        case (follow, repeat) => RepeatedLinearTraverse(follow, repeat)
      }
    }

    def traverses[_: P] = P(linearTraverse | nodeCheck | repeatedTraverse)
  }

  private def root[_: P] = {
    // implicit val whitespace = MultiLineWhitespace.whitespace
    P("root" ~ "{" ~/ (NodeId.targetSetting | NodeType.targetSetting | NodeAll.targetSetting) ~ "}") map {
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
