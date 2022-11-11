package models.query

import silvousplay.imports._
import models.{ IndexType, ESQuery, RepoSHAIndex }
import models.index.NodeType
import play.api.libs.json._
import models.graph._

case class GenericGraphTargeting(orgId: Int) extends QueryTargeting[GenericGraphUnit] {

  val nodeIndexName = GenericGraphNode.globalIndex
  val edgeIndexName = GenericGraphEdge.globalIndex

  val nodeSort = List("org_id" -> "asc", "id" -> "asc")
  val edgeSort = List("org_id" -> "asc", "from" -> "asc", "id" -> "asc")

  def innerQuery: JsObject = ESQuery.termSearch("org_id", orgId.toString)
  def additionalQuery: List[JsObject] = Nil

  /**
   * Graph
   */
  def edgeQuery(
    traverses: List[EdgeTypeTraverse],
    keys:      List[GenericGraphUnit],
    nodeHint:  Option[NodeType]): JsObject = {

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
            ESQuery.termsSearch("org_id", keys.map(_.orgId).distinct) :: Nil,
            ESQuery.termsSearch(direction.identifier, keys.map(_.id)) :: Nil,
            // ignore node hint for now
            // withDefined(nodeHint) { node =>
            //   // only use type hint for contains forward
            //   withFlag(t.forall(_.edgeType.isContainsForward)) {
            //     ESQuery.termSearch("toType", node.identifier) :: Nil
            //   }
            // },
            innerTypeQuery :: Nil).flatten)
      }
    }

    ESQuery.bool(
      filter = ESQuery.bool(
        should = traverseQueries.toList) :: Nil)

  }

  def nodeQuery(traces: List[GenericGraphUnit]): JsObject = {
    ESQuery.bool(
      must = List(
        ESQuery.termsSearch("org_id", traces.map(_.orgId)),
        ESQuery.termsSearch("id", traces.map(_.id))))
  }

  /**
   * Relational
   */
  def relationalKeyItem(unit: GenericGraphUnit): RelationalKeyItem = {
    RelationalKeyItem(unit.orgId, "", unit.id)
  }

}
