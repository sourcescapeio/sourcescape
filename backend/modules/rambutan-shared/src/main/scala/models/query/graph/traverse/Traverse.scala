package models.query

import models.ESQuery
import models.index.NodeType
import silvousplay.imports._
import play.api.libs.json._

sealed trait Traverse {

  //  this actually refers to whether or not we injectNew into the GraphTrace
  def isColumn: Boolean
}

object Traverse {
  def extractNameQuery(key: String, name: String) = {
    name.trim match {
      case n if n.startsWith("{") && n.endsWith("}") => {
        ESQuery.regexpSearch(key, name.drop(1).dropRight(1))
      }
      case n if n.contains("*") => ESQuery.wildcardSearch(key, n)
      case n                    => ESQuery.termSearch(key, n)
    }
  }
}

sealed case class EdgeTypeFollow(traverses: List[EdgeTypeTraverse]) {
  def reverse = this.copy(traverses = traverses.map(_.reverse))
}

object EdgeTypeFollow {
  def empty = EdgeTypeFollow(Nil)

  def all = GraphEdgeType.all.map(g => EdgeTypeTraverse(g, None)).toList
}

case class EdgeTypeTarget(traverses: List[EdgeTypeTraverse]) {
  def reverse = this.copy(traverses = traverses.map(_.reverse))
}

object EdgeTypeTarget {
  def empty = EdgeTypeTarget(Nil)
}

case class EdgeTypeTraverse(edgeType: GraphEdgeType, filter: Option[EdgeFilter]) {

  def flattened = (edgeType.edgeType, filter)

  def reverse = EdgeTypeTraverse(edgeType.opposite, filter)
}

object EdgeTypeTraverse {
  val BasicFollows = GraphEdgeType.follows

  def basic(edgeType: GraphEdgeType) = EdgeTypeTraverse(edgeType, None)
}

case class EdgeTraverse(follow: EdgeTypeFollow, target: EdgeTypeTarget, typeHint: Option[NodeType] = None) extends Traverse {

  def isColumn = (follow.traverses ++ target.traverses).nonEmpty

  def targetEdges = target.traverses.map(_.edgeType)
}

// traverses, emits all instead of spooling in a trace
case class RepeatedEdgeTraverse[T, TU](follow: EdgeTypeFollow, shouldTerminate: T => Boolean) extends Traverse {
  def isColumn = true

}

trait StatefulTeleport {
  def getNames(obj: JsObject): List[String]

  def nameQuery(name: List[String]): List[JsObject]

  def doJoin(node: JsObject, names: List[String], collectedMap: Map[String, List[JsObject]]): List[JsObject]
}

case class BasicStatefulTeleport(
  teleportNames: JsObject => List[String],
  teleportKey:   String) extends StatefulTeleport {

  def getNames(obj: JsObject) = teleportNames(obj)

  def nameQuery(names: List[String]): List[JsObject] = {
    ESQuery.termsSearch(teleportKey, names.toList) :: Nil
  }

  def doJoin(node: JsObject, names: List[String], collectedMap: Map[String, List[JsObject]]) = {
    names.flatMap { name =>
      collectedMap.getOrElse(name, Nil)
    }
  }
}

case class FilterTraverse(traverses: List[Traverse]) extends Traverse {

  override val isColumn: Boolean = false
}

case class ReverseTraverse(follow: EdgeTypeFollow, traverses: List[Traverse]) extends Traverse {
  def validate = {
    traverses.foreach {
      case a @ EdgeTraverse(_, _, _) => ()
      case n @ NodeTraverse(_, _)    => ()
      case f @ FilterTraverse(_)     => ()
      case other                     => throw new Exception("invalid traverse " + other)
    }
  }

  override val isColumn: Boolean = true
}

case class OneHopTraverse(follow: List[EdgeTypeTraverse]) extends Traverse {
  override val isColumn: Boolean = true

}

case class NodeTraverse(follow: EdgeTypeFollow, filters: List[NodeFilter]) extends Traverse {
  // Node traverse does not increment trace
  override val isColumn: Boolean = false

}
