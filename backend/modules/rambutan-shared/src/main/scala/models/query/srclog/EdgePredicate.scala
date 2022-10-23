package models.query

import models.{ IndexType, ESQuery }
import models.index.esprima.ESPrimaNodeType // TODO: ewww
import models.graph._
import models.query._
import silvousplay.imports._
import models.index.ruby.RubyNode
import play.api.libs.json._

sealed abstract class EdgePredicate(
  val identifier: String) extends Identifiable {

  /**
    * These definitely need to be set
    */
  // Assumption: at least one of these will be non-null
  // There is no ValidEdge from AnyNode to AnyNode except Assignment
  def fromImplicit: Option[NodePredicate] = None
  def toImplicit: Option[NodePredicate] = None

  def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]): List[Traverse]

  def reverseTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]): List[Traverse] = {
    ReverseTraverse(
      EdgeTypeFollow(follow.map(EdgeTypeTraverse.basic)),
      queryTraverse(name, index, props, Nil)) :: Nil
  }

  // used when emitting possible directed edges
  val forceForward: Boolean = false
  val forceReverse: Boolean = false

  /**
    * Cost parameters
    */
  val forwardCost: Int = 1
  val reverseCost: Int = 1
  // forward cost
  // backward cost



  /**
    * WTF are these used for??
    */
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

sealed abstract class ScalaEdgePredicate(identifierIn: String) extends EdgePredicate(s"scala::${identifierIn}")

object ScalaEdgePredicate extends Plenumeration[ScalaEdgePredicate] {

}

sealed abstract class RubyEdgePredicate(identifierIn: String) extends EdgePredicate(s"ruby::${identifierIn}")

object RubyEdgePredicate extends Plenumeration[RubyEdgePredicate] {

  case object Const extends RubyEdgePredicate("const") with HasName {
    // override val fromImplicit = Some(RubyNodePredicate.Const) // also CBase
    override val toImplicit = Some(RubyNodePredicate.Const)

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

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(RubyGraphEdgeType.PairValue, name) :: Nil)))
    }
  }
}

sealed abstract class JavascriptEdgePredicate(val identifierIn: String) extends EdgePredicate(s"javascript::${identifierIn}")

object JavascriptEdgePredicate extends Plenumeration[JavascriptEdgePredicate] {
  case object JSXTag extends JavascriptEdgePredicate("jsx_tag") {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)

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

  case object JSXAttribute extends JavascriptEdgePredicate("jsx_attribute") with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)
    override val toImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.JSXAttribute, name) :: Nil)))
    }
  }

  case object JSXAttributeValue extends JavascriptEdgePredicate("jsx_attribute_value") {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    // should egress?

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.JSXAttributeValue) :: Nil)) :: Nil
    }
  }

  case object JSXChild extends JavascriptEdgePredicate("jsx_child") {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)

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

  case object ClassExtends extends JavascriptEdgePredicate("class_extends") {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassExtends) :: Nil)) :: Nil
    }
  }

  case object InstanceOf extends JavascriptEdgePredicate("instance_of") {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.InstanceOf) :: Nil)) :: Nil
    }
  }

  case object InstanceArg extends JavascriptEdgePredicate("instance_arg") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    override val preferReverse: Boolean = true

    override val egressReferences = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          indexedEdge(JavascriptGraphEdgeType.ArgOf, index) :: Nil)) :: Nil
    }
  }

  case object ClassConstructor extends JavascriptEdgePredicate("class_constructor") with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ClassConstructor, name) :: Nil)))
    }

  }

  case object ClassMethod extends JavascriptEdgePredicate("class_method") with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(Nil),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ClassMethod, name) :: Nil)))
    }
  }

  case object ClassDecorator extends JavascriptEdgePredicate("class_decorator") {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassDecorator) :: Nil)) :: Nil
    }
  }

  case object ClassProperty extends JavascriptEdgePredicate("class_property") {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassProperty)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(Nil),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassProperty) :: Nil)) :: Nil
    }
  }

  case object ClassPropertyValue extends JavascriptEdgePredicate("class_property_value") {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassProperty)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.ClassPropertyValue) :: Nil)) :: Nil
    }
  }

  case object MethodArg extends JavascriptEdgePredicate("method_arg") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    override val preferReverse: Boolean = true

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
            indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index) :: Nil)))
    }
  }

  case object MethodDecorator extends JavascriptEdgePredicate("method_decorator") {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.MethodDecorator) :: Nil)) :: Nil
    }
  }

  @deprecated
  case object MethodContains extends JavascriptEdgePredicate("method_contains") {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

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

  case object FunctionArg extends JavascriptEdgePredicate("function_arg") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index) :: Nil)))
    }
  }

  case object FunctionContains extends JavascriptEdgePredicate("function_contains") {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)

    override val singleDirection = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)) :: Nil
    }
  }

  case object Return extends JavascriptEdgePredicate("return") {

    override val fromImplicit = Some(JavascriptNodePredicate.Return)

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

  case object Yield extends JavascriptEdgePredicate("yield") {

    override val fromImplicit = Some(JavascriptNodePredicate.Yield)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Yield) :: Nil)) :: Nil
    }
  }

  case object Await extends JavascriptEdgePredicate("await") {

    override val fromImplicit = Some(JavascriptNodePredicate.Await)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Await) :: Nil)) :: Nil
    }
  }

  case object Throw extends JavascriptEdgePredicate("throw") {
    override val fromImplicit = Some(JavascriptNodePredicate.Throw)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(
            JavascriptGraphEdgeType.Throw) :: Nil)) :: Nil
    }
  }

  case object Member extends JavascriptEdgePredicate("member") with HasName {
    override val toImplicit = Some(JavascriptNodePredicate.Member)

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

  case object Call extends JavascriptEdgePredicate("call") {
    override val toImplicit = Some(JavascriptNodePredicate.Call)

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
  case object CallArg extends JavascriptEdgePredicate("call_arg") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Call)

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

  case object IfCondition extends JavascriptEdgePredicate("if_condition") {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

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
  case object IfBody extends JavascriptEdgePredicate("if_body") {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

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

  case object BinaryLeft extends JavascriptEdgePredicate("binary_left") {

    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

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

  case object BinaryRight extends JavascriptEdgePredicate("binary_right") {
    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

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

  case object UnaryExpression extends JavascriptEdgePredicate("unary_expression") {
    override val fromImplicit = Some(JavascriptNodePredicate.UnaryExpression)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = edgeTypeFollow(follow),
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(JavascriptGraphEdgeType.BasicExpression) :: Nil)) :: Nil
    }
  }

  case object ArrayMember extends JavascriptEdgePredicate("array_member") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Array)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.ArrayMember, index) :: Nil)))
    }
  }

  case object ObjectProperty extends JavascriptEdgePredicate("object_property") with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Object)
    override val toImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ObjectProperty, name) :: Nil)))
    }
  }

  case object ObjectPropertyValue extends JavascriptEdgePredicate("object_property_value") with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    override val preferReverse: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            namedEdge(JavascriptGraphEdgeType.ObjectValue, name) :: Nil)))
    }
  }

  case object TemplateComponent extends JavascriptEdgePredicate("template_component") with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateLiteral)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(follow),
          target = EdgeTypeTarget(
            indexedEdge(JavascriptGraphEdgeType.TemplateLiteral, index) :: Nil)))
    }
  }

  case object TemplateContains extends JavascriptEdgePredicate("template_contains") {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateExpression)

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
  case object Contains extends JavascriptEdgePredicate("contains") {

    override def fromImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override val singleDirection = true

    // NOTE: body doesn't have egress because `*-contains` edges link directly to all nodes

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      List(
        EdgeTraverse(
          follow = edgeTypeFollow(JavascriptGraphEdgeType.MethodFunction :: follow),
          target = EdgeTypeTarget(
            EdgeTypeTraverse.basic(JavascriptGraphEdgeType.FunctionContains) :: Nil)))
    }
  }

  case object AllCalled extends JavascriptEdgePredicate("all_called") {
    override def fromImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override def toImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    override val singleDirection: Boolean = true

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
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

sealed abstract class GenericEdgePredicate(val identifierIn: String) extends EdgePredicate(s"generic::${identifierIn}") {
  override val suppressNodeCheck = true
}

sealed class BasicGenericEdgePredicate(from: GenericGraphNodePredicate, to: GenericGraphNodePredicate, edgeType: GenericGraphEdgeType)
  extends GenericEdgePredicate(edgeType.identifier) {
  override val fromImplicit = Some(from)
  override val toImplicit = Some(to)

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
  case object TableRow extends GenericEdgePredicate("table_row") {
    override val fromImplicit = Some(GenericGraphNodePredicate.Table)
    override val toImplicit = Some(GenericGraphNodePredicate.Row)

    override def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]) = {
      EdgeTraverse(
        follow = EdgeTypeFollow.empty,
        target = EdgeTypeTarget(
          EdgeTypeTraverse.basic(GenericGraphEdgeType.TableRow) :: Nil)) :: Nil
    }
  }

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
