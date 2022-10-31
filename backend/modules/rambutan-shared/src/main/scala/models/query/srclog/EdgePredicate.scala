package models.query

import models.{ IndexType, ESQuery }
import models.index.esprima.ESPrimaNodeType // TODO: ewww
import models.graph._
import models.query._
import silvousplay.imports._
import models.index.ruby.RubyNode
import play.api.libs.json._

sealed abstract class EdgePredicate(
  val identifier:  String,
  val forwardCost: Int,
  val reverseCost: Int) extends Identifiable with QueryBuilderHelpers {

  /**
   * New style variables
   */
  // At least one of these should be non-null
  def fromImplicit: Option[NodePredicate] = None
  def toImplicit: Option[NodePredicate] = None

  def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse

  //

  // def propagatedFollow =

  /**
   * Feature flags
   */
  // used when emitting possible directed edges
  val forceForward: Boolean = false
  val forceReverse: Boolean = false // may deprecate this and just rely on costs

  //
  val repeated: Boolean = false

  // requires specific nodes for both sides
  val mustSpecifyNodes: Boolean = false

  /**
   * LEGACY PARAMETERS. To deprecate
   */
  def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]): List[Traverse]

  final def reverseTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]): List[Traverse] = {
    ReverseTraverse(
      EdgeTypeFollow(follow.map(EdgeTypeTraverse.basic)),
      queryTraverse(name, index, props, Nil)) :: Nil
  }

  // reversal preferences
  val suppressNodeCheck: Boolean = false
  val forceForwardDirection: Boolean = false // can only go in forward direction
  val singleDirection: Boolean = false // TODO: wtf is with this one?
  val preferReverse: Boolean = false
  def shouldReverseMissing = singleDirection || preferReverse

  // these determine whether must node traverse
  // note: these are sort of determined by whether ingress or egress accepts AnyNode
  val ingressReferences: Boolean = false
  val egressReferences: Boolean = false

  protected def edgeTypeFollow(follows: List[GraphEdgeType]) = {
    EdgeTypeFollow(follows.map(EdgeTypeTraverse.basic))
  }
}

sealed trait QueryBuilderHelpers {
  protected def ?(et: EdgeTypeTraverse*): EdgeFollow = {
    EdgeFollow(et.toList, FollowType.Optional)
  }

  protected def *(et: EdgeTypeTraverse*): EdgeFollow = {
    EdgeFollow(et.toList, FollowType.Star)
  }

  protected def t(et: EdgeTypeTraverse*): EdgeFollow = {
    EdgeFollow(et.toList, FollowType.Target)
  }

  protected def basic(g: GraphEdgeType) = {
    EdgeTypeTraverse(g, None)
  }

  protected def lin(follows: EdgeFollow*) = {
    LinearTraverse(follows.toList)
  }
}

sealed trait HasIndex {
  self: EdgePredicate =>

  protected def indexedEdge(edgeType: GraphEdgeType, index: Option[Int]) = {
    EdgeTypeTraverse(
      edgeType,
      index.map(i => EdgeIndexFilter(i - 1)))
  }
}

sealed trait HasName {
  self: EdgePredicate =>

  protected def namedEdge(edgeType: GraphEdgeType, name: Option[String]) = {
    EdgeTypeTraverse(
      edgeType,
      name.map(n => EdgeNameFilter(n)))
  }
}

sealed abstract class RubyEdgePredicate(identifierIn: String) extends EdgePredicate(
  s"ruby::${identifierIn}",
  forwardCost = 10,
  reverseCost = 1 // ZFG
)

object RubyEdgePredicate extends Plenumeration[RubyEdgePredicate] {

  case object Const extends RubyEdgePredicate("const") with HasName {
    // override val fromImplicit = Some(RubyNodePredicate.Const) // also CBase
    override val toImplicit = Some(RubyNodePredicate.Const)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.Const, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(RubyGraphEdgeType.Const, name) :: Nil)))
    }
  }

  case object Send extends RubyEdgePredicate("send") with HasName {
    override val toImplicit = Some(RubyNodePredicate.Send)

    override val ingressReferences = true

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.Send, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(RubyGraphEdgeType.Send, name) :: Nil)))
    }
  }

  case object SendArg extends RubyEdgePredicate("send-arg") with HasIndex {
    override val fromImplicit = Some(RubyNodePredicate.Send)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(RubyGraphEdgeType.SendArg, index)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            indexedEdge(RubyGraphEdgeType.SendArg, index) :: Nil)))
    }
  }

  case object ArrayElement extends RubyEdgePredicate("array-element") with HasIndex {
    override val fromImplicit = Some(RubyNodePredicate.Array)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(RubyGraphEdgeType.ArrayElement, index)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            indexedEdge(RubyGraphEdgeType.ArrayElement, index) :: Nil)))
    }
  }

  case object HashElement extends RubyEdgePredicate("hash-element") with HasName {
    override val fromImplicit = Some(RubyNodePredicate.Hash)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.HashElement, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(RubyGraphEdgeType.HashElement, name) :: Nil)))
    }
  }

  case object PairValue extends RubyEdgePredicate("pair-value") with HasName {
    override val fromImplicit = Some(RubyNodePredicate.HashPair)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.PairValue, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(RubyGraphEdgeType.PairValue, name) :: Nil)))
    }
  }
}

sealed abstract class JavascriptEdgePredicate(identifierIn: String, forwardCost: Int, reverseCost: Int) extends EdgePredicate(
  s"javascript::${identifierIn}",
  forwardCost,
  reverseCost)

object JavascriptEdgePredicate extends Plenumeration[JavascriptEdgePredicate] {
  case object JSXTag extends JavascriptEdgePredicate("jsx_tag", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXTag)))
    }

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.JSXTag) :: Nil)) :: Nil
    }
  }

  case object JSXAttribute extends JavascriptEdgePredicate("jsx_attribute", forwardCost = 5, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)
    override val toImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.JSXAttribute, name)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.JSXAttribute, name) :: Nil)))
    }
  }

  case object JSXAttributeValue extends JavascriptEdgePredicate("jsx_attribute_value", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    // should egress?
    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXAttributeValue)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.JSXAttributeValue) :: Nil)) :: Nil
    }
  }

  case object JSXChild extends JavascriptEdgePredicate("jsx_child", forwardCost = 10, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXChild)))
    }

    // override val singleDirection = true
    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.JSXChild) :: Nil)) :: Nil
    }
  }

  case object ClassExtends extends JavascriptEdgePredicate("class_extends", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassExtends)))
    }

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassExtends) :: Nil)) :: Nil
    }
  }

  case object InstanceOf extends JavascriptEdgePredicate("instance_of", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.InstanceOf)))
    }

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.InstanceOf) :: Nil)) :: Nil
    }
  }

  case object InstanceArg extends JavascriptEdgePredicate("instance_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArgOf, index)))
    }

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          indexedEdge(JavascriptGraphEdgeType.ArgOf, index) :: Nil)) :: Nil
    }
  }

  case object ClassConstructor extends JavascriptEdgePredicate("class_constructor", forwardCost = 1, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassConstructor, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ClassConstructor, name) :: Nil)))
    }

  }

  case object ClassMethod extends JavascriptEdgePredicate("class_method", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassMethod, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ClassMethod, name) :: Nil)))
    }
  }

  case object ClassDecorator extends JavascriptEdgePredicate("class_decorator", forwardCost = 2, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassDecorator)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassDecorator) :: Nil)) :: Nil
    }
  }

  case object ClassProperty extends JavascriptEdgePredicate("class_property", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassProperty)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassProperty, name)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassProperty) :: Nil)) :: Nil
    }
  }

  case object ClassPropertyValue extends JavascriptEdgePredicate("class_property_value", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassProperty)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassPropertyValue)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassPropertyValue) :: Nil)) :: Nil
    }
  }

  case object MethodArg extends JavascriptEdgePredicate("method_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(
              JavascriptGraphEdgeType.MethodFunction) :: Nil)),
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index) :: Nil)))
    }
  }

  case object MethodDecorator extends JavascriptEdgePredicate("method_decorator", forwardCost = 2, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodDecorator)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.MethodDecorator) :: Nil)) :: Nil
    }
  }

  @deprecated
  case object MethodContains extends JavascriptEdgePredicate("method_contains", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }

    override val mustSpecifyNodes = true

    override val singleDirection = true

    // NOTE: body doesn't have egress because `*-contains` edges link directly to all nodes

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(
              JavascriptGraphEdgeType.MethodFunction) :: Nil)),
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)))
    }
  }

  case object FunctionArg extends JavascriptEdgePredicate("function_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index) :: Nil)))
    }
  }

  @deprecated
  case object FunctionContains extends JavascriptEdgePredicate("function_contains", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }

    override val mustSpecifyNodes = true

    override val singleDirection = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)) :: Nil
    }
  }

  case object Return extends JavascriptEdgePredicate("return", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Return)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Return)))
    }

    override val preferReverse: Boolean = true
    // override val singleDirection = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            // ReturnContains
            JavascriptGraphEdgeType.Return) :: Nil)) :: Nil
    }
  }

  case object Yield extends JavascriptEdgePredicate("yield", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Yield)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Yield)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Yield) :: Nil)) :: Nil
    }
  }

  case object Await extends JavascriptEdgePredicate("await", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Await)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Await)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Await) :: Nil)) :: Nil
    }
  }

  case object Throw extends JavascriptEdgePredicate("throw", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Throw)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Throw)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Throw) :: Nil)) :: Nil
    }
  }

  case object Member extends JavascriptEdgePredicate("member", forwardCost = 1, reverseCost = 1) with HasName {
    override val toImplicit = Some(JavascriptNodePredicate.Member)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        *(JavascriptGraphEdgeType.follows.map(basic): _*),
        t(namedEdge(JavascriptGraphEdgeType.MemberOf, name)))
    }

    override val preferReverse: Boolean = true

    override val ingressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow), // will be replaced
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.MemberOf, name) :: Nil)))
    }
  }

  case object Call extends JavascriptEdgePredicate("call", forwardCost = 1, reverseCost = 1) {
    override val toImplicit = Some(JavascriptNodePredicate.Call)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        *(JavascriptGraphEdgeType.follows.map(basic): _*),
        t(basic(JavascriptGraphEdgeType.CallOf)))
    }

    override val preferReverse: Boolean = true

    override val ingressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow), // will be replaced
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.CallOf) :: Nil)) :: Nil
    }
  }

  case object CallArg extends JavascriptEdgePredicate("call_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Call)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArgOf, index)))
    }

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.ArgOf, index) :: Nil)))
    }
  }

  case object IfCondition extends JavascriptEdgePredicate("if_condition", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.IfBlock)),
        t(basic(JavascriptGraphEdgeType.IfTest)))
    }

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.IfBlock) :: Nil)) ::
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            // IfTestContains
            EdgeTypeTraverse.basic(JavascriptGraphEdgeType.IfTest) :: Nil)) :: Nil
    }
  }

  case object IfBody extends JavascriptEdgePredicate("if_body", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.IfBlock)),
        t(basic(JavascriptGraphEdgeType.IfContains)))
    }

    override val mustSpecifyNodes = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.IfBlock) :: Nil)) ::
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(JavascriptGraphEdgeType.IfContains) :: Nil)) :: Nil
    }

    override val singleDirection = true
  }

  case object BinaryLeft extends JavascriptEdgePredicate("binary_left", forwardCost = 1, reverseCost = 1) with HasIndex {

    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.BasicExpression, Some(0))))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse(
            JavascriptGraphEdgeType.BasicExpression,
            Option(EdgeIndexFilter(0))) :: Nil)) :: Nil
    }
  }

  case object BinaryRight extends JavascriptEdgePredicate("binary_right", forwardCost = 1, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.BasicExpression, Some(0))))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse(
            JavascriptGraphEdgeType.BasicExpression,
            Option(EdgeIndexFilter(1))) :: Nil)) :: Nil
    }
  }

  case object UnaryExpression extends JavascriptEdgePredicate("unary_expression", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.UnaryExpression)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.BasicExpression)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.BasicExpression) :: Nil)) :: Nil
    }
  }

  case object ArrayMember extends JavascriptEdgePredicate("array_member", forwardCost = 10, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Array)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArrayMember, index)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.ArrayMember, index) :: Nil)))
    }
  }

  case object ObjectProperty extends JavascriptEdgePredicate("object_property", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Object)
    override val toImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ObjectProperty, name)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ObjectProperty, name) :: Nil)))
    }
  }

  case object ObjectPropertyValue extends JavascriptEdgePredicate("object_property_value", forwardCost = 1, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ObjectValue, name)))
    }

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ObjectValue, name) :: Nil)))
    }
  }

  case object TemplateComponent extends JavascriptEdgePredicate("template_component", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateLiteral)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.TemplateLiteral, index)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.TemplateLiteral, index) :: Nil)))
    }
  }

  case object TemplateContains extends JavascriptEdgePredicate("template_contains", forwardCost = 100, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateExpression)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.TemplateContains)))
    }

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.TemplateContains) :: Nil)) :: Nil
    }
  }

  /**
   * Experimental Repeated Traverses
   */
  case object Contains extends JavascriptEdgePredicate("contains", forwardCost = 1000, reverseCost = 1) {

    override def fromImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override val mustSpecifyNodes = true

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        ?(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }

    override val singleDirection = true

    // NOTE: body doesn't have egress because `*-contains` edges link directly to all nodes

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      // ?(MethodFunction)
      // T(FunctionContains)

      List(
        EdgeTraverse(
          follow = edgeTypeFollow(JavascriptGraphEdgeType.MethodFunction :: follow),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)))
    }
  }

  case object AllCalled extends JavascriptEdgePredicate("all_called", forwardCost = 100000, reverseCost = 1) {
    override def fromImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override def toImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override val repeated: Boolean = true

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        ?(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)),
        t(basic(JavascriptGraphEdgeType.CallOf)))
    }

    //
    override val singleDirection: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      // ?(MethodFunction)
      // T(FunctionContains)
      // T(CallLink)
      RepeatedEdgeTraverseNew(
        List(
          EdgeTraverse(
            follow = edgeTypeFollow(JavascriptGraphEdgeType.MethodFunction :: Nil),
            target = EdgeTypeTarget(EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)),
          EdgeTraverse(
            follow = edgeTypeFollow(Nil),
            target = EdgeTypeTarget(EdgeTypeTraverse.basic(JavascriptGraphEdgeType.CallLink) :: Nil)))) :: Nil
    }
  }

}

sealed abstract class GenericEdgePredicate(val identifierIn: String) extends EdgePredicate(s"generic::${identifierIn}", forwardCost = 1, reverseCost = 1) {
  override val suppressNodeCheck = true
}

sealed class BasicGenericEdgePredicate(from: GenericGraphNodePredicate, to: GenericGraphNodePredicate, edgeType: GenericGraphEdgeType)
  extends GenericEdgePredicate(edgeType.identifier) {
  override val fromImplicit = Some(from)
  override val toImplicit = Some(to)

  def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
    lin(
      t(EdgeTypeTraverse(edgeType, ifNonEmpty(props) {
        Option {
          EdgePropsFilter(props)
        }
      })))
  }

  override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
    EdgeTraverse(
      follow = EdgeTypeFollow.empty,
      target = EdgeTypeTarget(
        EdgeTypeTraverse(
          edgeType,
          ifNonEmpty(props) {
            Option {
              EdgePropsFilter(props)
            }
          }) :: Nil)) :: Nil
  }
}

object GenericGraphEdgePredicate extends Plenumeration[GenericEdgePredicate] {

  /**
   * Gits
   */
  case object GitHeadCommit extends BasicGenericEdgePredicate(
    from = GenericGraphNodePredicate.GitHead,
    to = GenericGraphNodePredicate.GitCommit,
    edgeType = GenericGraphEdgeType.GitHeadCommit)

  case object GitCommitIndex extends BasicGenericEdgePredicate(
    from = GenericGraphNodePredicate.GitCommit,
    to = GenericGraphNodePredicate.CodeIndex,
    edgeType = GenericGraphEdgeType.GitCommitIndex)

  // fancy
  case object GitCommitParent extends GenericEdgePredicate("git-commit-parent") {
    // we don't want to attach a node clause because we're emitting multiple

    // override val fromImplicit = Some(GenericGraphNodePredicate.GitCommit)
    // override val toImplicit = Some(GenericGraphNodePredicate.GitCommit)

    // disgusting
    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      val maybeCommit = props.find(_.key =?= "commit").map(_.value)
      val maybeLimit = props.find(_.key =?= "limit").map(_.value.toInt)

      RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
        EdgeTypeFollow(
          EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent, filter = None) :: Nil),
        { trace =>
          val limitTerminate = maybeLimit.map((trace.tracesInternal.length + 1) >= _).getOrElse(false)
          val commitTerminate = maybeCommit.map(trace.terminusId.id =?= _).getOrElse(false)
          limitTerminate || commitTerminate
        })
    }

    override val forceForward = true

    override val forceForwardDirection = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      val maybeCommit = props.find(_.key =?= "commit").map(_.value)
      val maybeLimit = props.find(_.key =?= "limit").map(_.value.toInt)

      RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
        EdgeTypeFollow(
          EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent, filter = None) :: Nil),
        { trace =>
          val limitTerminate = maybeLimit.map((trace.tracesInternal.length + 1) >= _).getOrElse(false)
          val commitTerminate = maybeCommit.map(trace.terminusId.id =?= _).getOrElse(false)
          limitTerminate || commitTerminate
        }) :: Nil
    }
  }

  case object GitCommitChild extends GenericEdgePredicate("git-commit-child") {
    // we don't want to attach a node clause because we're emitting multiple

    // override val fromImplicit = Some(GenericGraphNodePredicate.GitCommit)
    // override val toImplicit = Some(GenericGraphNodePredicate.GitCommit)

    def newQueryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      val maybeCommit = props.find(_.key =?= "commit").map(_.value)
      val maybeLimit = props.find(_.key =?= "limit").map(_.value.toInt)

      RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
        EdgeTypeFollow(
          EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent.opposite, filter = None) :: Nil),
        { trace =>
          val limitTerminate = maybeLimit.map((trace.tracesInternal.length + 1) >= _).getOrElse(false)
          val commitTerminate = maybeCommit.map(trace.terminusId.id =?= _).getOrElse(false)
          limitTerminate || commitTerminate
        })
    }

    override val forceForward = true

    override val forceForwardDirection = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      val maybeCommit = props.find(_.key =?= "commit").map(_.value)
      val maybeLimit = props.find(_.key =?= "limit").map(_.value.toInt)

      RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
        EdgeTypeFollow(
          EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent.opposite, filter = None) :: Nil),
        { trace =>
          val limitTerminate = maybeLimit.map((trace.tracesInternal.length + 1) >= _).getOrElse(false)
          val commitTerminate = maybeCommit.map(trace.terminusId.id =?= _).getOrElse(false)
          limitTerminate || commitTerminate
        }) :: Nil
    }
  }
}

// we only use reads here
object EdgePredicate extends Plenumeration[EdgePredicate] {
  override val all = {
    GenericGraphEdgePredicate.all ++ IndexType.all.flatMap(_.edgePredicate.all)
  }
}
