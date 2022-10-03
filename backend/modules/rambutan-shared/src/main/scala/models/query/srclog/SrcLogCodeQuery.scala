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
  selected: List[String]) extends SrcLogQuery {

  def withSelected(ids: List[String]) = {
    this.copy(selected = ids)
  }

  // parent, toStart, toEnd
  def remapContainsEdges(remapEdges: List[(String, String, String)]) = {
    val remapSet = remapEdges.map { r =>
      (r._1, r._2)
    }.toSet
    val remapMap = remapEdges.map { r =>
      (r._2, r._3)
    }.toMap
    val remapped = this.edges.map {
      case e @ EdgeClause(p, from, to, cond, mod) if BuilderEdgeType.fromPredicate(p).isContains => {
        val shouldRemap = remapSet.contains((from, to))
        remapMap.get(to) match {
          case Some(remappedTo) if shouldRemap => EdgeClause(p, from, remappedTo, cond, mod)
          case _                               => e
        }
      }
      case o => o
    }
    this.copy(edges = remapped)
  }

  def setCondition(id: String, parentId: Option[String], conditionType: ConditionType, condition: Option[Condition]) = {
    // node will blindly copy condition
    val resetNodes = nodes.map {
      case n if n.variable =?= id && parentId.isEmpty => n.copy(condition = condition)
      case n => n
    }

    val resetEdges = edges.map { e =>
      val edgeMatch = e.to =?= id && (parentId.isEmpty || parentId =?= Some(e.from))

      val shouldReplace = conditionType match {
        case ConditionType.Name if e.predicate.hasName => true
        case ConditionType.Index if e.predicate.hasIndex => true
        case _ => false
      }

      if (edgeMatch && shouldReplace) {
        e.copy(condition = condition)
      } else {
        e
      }
    }

    this.copy(
      nodes = resetNodes,
      edges = resetEdges)
  }

  def rename(id: String, name: String) = {
    setCondition(id, None, ConditionType.Name, Some(NameCondition(name)))
  }

  def unsetName(id: String) = {
    setCondition(id, None, ConditionType.Name, None) // also deletes index. yikes
  }

  def setIndex(id: String, parentId: Option[String], index: Int) = {
    setCondition(id, parentId, ConditionType.Index, Some(IndexCondition(index)))
  }

  def unsetIndex(id: String, parentId: Option[String]) = {
    setCondition(id, parentId, ConditionType.Index, None) // also deletes name. yikes
  }

  def delete(ids: List[String]) = {
    this.copy(
      nodes = nodes.filterNot(ids contains _.variable),
      edges = edges.filterNot(e => ids.contains(e.from) || ids.contains(e.to)),
      aliases = aliases.filterNot(ids contains _._1))
  }

  def deleteEdge(from: String, to: String) = {
    this.copy(
      edges = edges.filterNot(e => e.from =?= from && e.to =?= to))
  }

  def deleteEdges(edges: List[(String, String)]) = {
    edges.foldLeft(this) {
      case (acc, (from, to)) => this.deleteEdge(from, to)
    }
  }

  def addAlias(id: String, name: String) = {
    this.copy(aliases = aliases + (id -> name))
  }

  def addAliases(newAliases: Map[String, String]) = {
    this.copy(aliases = aliases ++ newAliases)
  }

  def addNode(node: NodeClause) = {
    this.copy(nodes = nodes :+ node)
  }

  def addNodes(newNodes: List[NodeClause]) = {
    this.copy(nodes = nodes ++ newNodes)
  }

  def addEdge(edge: EdgeClause) = {
    this.copy(edges = edges :+ edge)
  }

  def addEdges(newEdges: List[EdgeClause]) = {
    this.copy(edges = edges ++ newEdges)
  }

  def nextVertexId: String = {
    incVertexId(0)
  }

  def incVertexId(inc: Int) = {
    val max = vertexes.maxByOption(i => i).map(i => Base26.idToInt(i, 0)).getOrElse(0) // one below a
    Base26.intToId(max + inc)
  }

  lazy val singleDirectionNodes: Set[String] = {
    edges.flatMap {
      case EdgeClause(p, from, _, _, _) if p.singleDirection => Some(from)
      case _ => None
    }.toSet
  }

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
  private def selectDirective[_: P] = P("%select(" ~ SrcLogQuery.varChars ~ ("," ~ SrcLogQuery.varChars).rep(0) ~ ")" ~ ".") map {
    case (a, bs) => a :: bs.toList
  }

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
