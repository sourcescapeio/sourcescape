package models.query

import silvousplay.imports._
import models.{ IndexType, ESQuery, RepoSHAIndex }
import models.index.NodeType
import play.api.libs.json._

/**
 * Repo targeting
 */
case class TargetingDiffKey(rootKey: String, diffKey: String, files: List[String]) {
  def query = ESQuery.bool(
    should = List(
      ESQuery.termSearch("key", diffKey),
      ESQuery.bool(
        must = ESQuery.termSearch("key", rootKey) :: Nil,
        mustNot = ESQuery.termsSearch("path", files) :: Nil)))
}

case class KeysQueryTargeting(
  indexType:  IndexType,
  indexes:    List[RepoSHAIndex],
  diffMap:    Map[Int, List[String]],
  fileFilter: Option[List[String]]) extends QueryTargeting[TraceUnit] {

  val resultType = QueryResultType.GraphTrace

  //inherits
  val extractor = implicitly[HasBasicExtraction[TraceUnit]]

  val nodeIndexName = indexType.nodeIndexName
  val edgeIndexName = indexType.edgeIndexName

  val nodeSort = List("key" -> "asc", "path" -> "asc", "id" -> "asc")
  val edgeSort = List("key" -> "asc", "from" -> "asc", "id" -> "asc")

  // base computed
  val cleanKeys = indexes.filterNot(_.isDiff).map(_.esKey)

  val diffKeys = indexes.flatMap { index =>
    // these better all have rootKeys
    // Option[T]
    for {
      rootId <- index.rootIndexId
      rootKey <- index.rootKey
    } yield {
      val shaKey = index.esKey
      TargetingDiffKey(rootKey, shaKey, diffMap.getOrElse(index.id, Nil))
    }
  }

  // computed
  def allKeys = cleanKeys ++ diffKeys.map(_.diffKey)

  def repoIds = indexes.map(_.repoId).distinct

  def additionalQuery = withDefined(fileFilter) {
    case f :: Nil if f.contains("*") => ESQuery.wildcardSearch("path", f) :: Nil
    case fs                          => ESQuery.termsSearch("path", fs) :: Nil
  }

  def innerQuery = {
    val cleanQuery = ESQuery.termsSearch("key", cleanKeys.distinct)
    val diffQuery = diffKeys.map(_.query)
    (cleanKeys, diffKeys) match {
      case (_, Nil) => {
        cleanQuery
      }
      case (Nil, _) => {
        ESQuery.bool(should = diffQuery)
      }
      case _ => {
        ESQuery.bool(
          should = cleanQuery :: diffQuery)
      }
    }
  }
  /**
   * Graph Basic
   */
  def edgeQuery(
    traverses: List[EdgeTypeTraverse],
    keys:      List[TraceUnit],
    nodeHint:  Option[NodeType]) = {

    val traverseQueries = traverses.groupBy(_.edgeType.direction).map {
      case (direction, t) => {
        val directionalTraverses = t.map(_.flattened)

        val noFilter = directionalTraverses.flatMap {
          case (k, None) => Some(k)
          case _         => None
        }

        val hasFilter = directionalTraverses.flatMap {
          case (k, Some(f)) => Some(ESQuery.bool(
            must = List(
              ESQuery.termSearch("type", k.identifier),
              f.query)))
          case _ => None
        }

        val innerTypeQuery = ESQuery.bool(
          should = ifNonEmpty(noFilter) {
            List(ESQuery.termsSearch("type", noFilter.map(_.identifier)))
          } ++ hasFilter)

        ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", keys.map(_.key).distinct) :: Nil,
            ESQuery.termsSearch(direction.identifier, keys.map(_.id)) :: Nil,
            withDefined(nodeHint) { node =>
              // only use type hint for contains forward
              withFlag(t.forall(_.edgeType.isContainsForward)) {
                ESQuery.termSearch("toType", node.identifier) :: Nil
              }
            },
            innerTypeQuery :: Nil).flatten)
      }
    }

    ESQuery.bool(
      filter = ESQuery.bool(
        should = traverseQueries.toList) :: Nil)
  }

  def nodeQuery(traces: List[TraceUnit]) = {
    ESQuery.bool(
      must = List(
        ESQuery.termsSearch("key", traces.map(_.key).distinct),
        ESQuery.termsSearch("path", traces.map(_.path).distinct), // really necessary?
        ESQuery.termsSearch("id", traces.map(_.id))).distinct)
  }

  def traceHop(unit: TraceUnit, edgeType: GraphEdgeType, edgeJs: JsObject) = {
    val oppositeId = edgeType.direction.extractOpposite(edgeJs)

    unit.copy(
      edgeType = Some(edgeType),
      name = (edgeJs \ "_source" \ "name").asOpt[String],
      index = (edgeJs \ "_source" \ "index").asOpt[Int],
      id = oppositeId)
  }

  /**
   * Graph FSM
   */
  def calculateUnwindSequence(traverse: StatefulTraverse, trace: GraphTrace[TraceUnit]): List[EdgeTypeTarget] = {
    (trace.terminus.tracesInternal ++ List(trace.terminusId)).flatMap { e =>
      // Option[T]
      for {
        edgeType <- e.edgeType
        targets <- traverse.mapping.get(edgeType)
        edgeTypeTarget <- ifNonEmpty(targets) {
          Option {
            EdgeTypeTarget(targets.map { t =>
              val filter = (e.name, e.index) match {
                case (Some(n), _) => Some(EdgeNameFilter(n))
                case (_, Some(i)) => Some(EdgeIndexFilter(i))
                case _            => None
              }
              EdgeTypeTraverse(t, filter)
            })
          }
        }
      } yield {
        edgeTypeTarget
      }
    }
  }

  /**
   * Relational
   */
  def relationalKeyItem(unit: TraceUnit) = RelationalKeyItem(unit.key, unit.path, unit.id)
}
