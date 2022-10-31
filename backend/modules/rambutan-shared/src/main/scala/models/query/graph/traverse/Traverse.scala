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
      case n if n.startsWith("/") && n.endsWith("/") => {
        ESQuery.regexpSearch(key, name.drop(1).dropRight(1))
      }
      case n if n.contains("*") => ESQuery.wildcardSearch(key, n)
      case n                    => ESQuery.termSearch(key, n)
    }
  }
}

case class EdgeTypeTraverse(edgeType: GraphEdgeType, filter: Option[EdgeFilter]) {

  def flattened = (edgeType.edgeType, filter)

  def reverse = EdgeTypeTraverse(edgeType.opposite, filter)
}

object EdgeTypeTraverse {
  val BasicFollows = GraphEdgeType.follows

  def basic(edgeType: GraphEdgeType) = EdgeTypeTraverse(edgeType, None)
}

sealed abstract class FollowType(val identifier: String) extends Identifiable

object FollowType extends Plenumeration[FollowType] {
  case object Optional extends FollowType("?")
  case object Star extends FollowType("*")
  case object Target extends FollowType("t")
}

sealed trait SrcLogTraverse extends Traverse

case class EdgeFollow(traverses: List[EdgeTypeTraverse], followType: FollowType) {
  def reverse = this.copy(traverses = traverses.map(_.reverse))
}

case class LinearTraverse(follows: List[EdgeFollow]) extends SrcLogTraverse {
  // ???
  def isColumn = follows.flatMap(_.traverses).nonEmpty
}

case class RepeatedLinearTraverse(follows: List[EdgeFollow], repeated: List[EdgeFollow]) extends Traverse {
  def isColumn = true
}

// Still used by Git
@deprecated
case class RepeatedEdgeTraverse[T, TU](follow: EdgeFollow, shouldTerminate: T => Boolean) extends SrcLogTraverse {
  def isColumn = true

}

// switch to NodeCheck that does not follow
case class NodeCheck(filters: List[NodeFilter]) extends Traverse {
  // Node traverse does not increment trace
  override val isColumn: Boolean = false

}
