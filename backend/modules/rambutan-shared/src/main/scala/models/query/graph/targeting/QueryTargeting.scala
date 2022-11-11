package models.query

import silvousplay.imports._
import models.{ IndexType, ESQuery, RepoSHAIndex }
import models.index.NodeType
import play.api.libs.json._
import models.graph._

trait HasBasicExtraction[T] {
  self =>
  def getId(unit: T): String
  def getKey(unit: T): String
}

trait HasTraceKey[T] {
  def edgeTypeIdentifier(t: T): String

  def traceKey(t: T): TraceKey
}

// Actual targeting object
trait QueryTargeting[T] {

  /**
   * Index level stuff
   */
  val nodeIndexName: String
  val edgeIndexName: String

  val nodeSort: List[(String, String)]
  val edgeSort: List[(String, String)]

  def innerQuery: JsObject
  def additionalQuery: List[JsObject]

  final def rootQuery(root: GraphRoot): JsObject = {
    val inner = this.innerQuery :: root.query ++ this.additionalQuery

    ESQuery.bool(
      filter = ESQuery.bool(
        must = inner) :: Nil)
  }

  /**
   * Graph
   */
  def edgeQuery(
    traverses: List[EdgeTypeTraverse],
    keys:      List[T],
    nodeHint:  Option[NodeType]): JsObject

  def nodeQuery(traces: List[T]): JsObject

  /**
   * Relational
   */
  def relationalKeyItem(unit: T): RelationalKeyItem
}
