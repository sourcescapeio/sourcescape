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

  def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse

  /**
   * Feature flags
   */
  // Repeats the traversal
  val repeated: Boolean = false

  // requires specific nodes for both sides
  val mustSpecifyNodes: Boolean = false

  /**
   * Legacy feature flags
   */
  def shouldReverseMissing: Boolean = toImplicit.isEmpty || (fromImplicit.isDefined && (reverseCost < forwardCost))
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
      index.map(i => EdgeIndexesFilter(List(i - 1))))
  }
}

sealed trait HasName {
  self: EdgePredicate =>

  protected def namedEdge(edgeType: GraphEdgeType, name: Option[String]) = {
    EdgeTypeTraverse(
      edgeType,
      name.map(n => EdgeNamesFilter(List(n))))
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

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.Const, name)))
    }
  }

  case object Send extends RubyEdgePredicate("send") with HasName {
    override val toImplicit = Some(RubyNodePredicate.Send)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.Send, name)))
    }
  }

  case object SendArg extends RubyEdgePredicate("send-arg") with HasIndex {
    override val fromImplicit = Some(RubyNodePredicate.Send)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(RubyGraphEdgeType.SendArg, index)))
    }
  }

  case object ArrayElement extends RubyEdgePredicate("array-element") with HasIndex {
    override val fromImplicit = Some(RubyNodePredicate.Array)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(RubyGraphEdgeType.ArrayElement, index)))
    }
  }

  case object HashElement extends RubyEdgePredicate("hash-element") with HasName {
    override val fromImplicit = Some(RubyNodePredicate.Hash)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.HashElement, name)))
    }
  }

  case object PairValue extends RubyEdgePredicate("pair-value") with HasName {
    override val fromImplicit = Some(RubyNodePredicate.HashPair)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(RubyGraphEdgeType.PairValue, name)))
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

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXTag)))
    }
  }

  case object JSXAttribute extends JavascriptEdgePredicate("jsx_attribute", forwardCost = 5, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)
    override val toImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.JSXAttribute, name)))
    }
  }

  case object JSXAttributeValue extends JavascriptEdgePredicate("jsx_attribute_value", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXAttribute)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXAttributeValue)))
    }
  }

  case object JSXChild extends JavascriptEdgePredicate("jsx_child", forwardCost = 10, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.JSXElement)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.JSXChild)))
    }
  }

  case object ClassExtends extends JavascriptEdgePredicate("class_extends", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassExtends)))
    }
  }

  case object InstanceOf extends JavascriptEdgePredicate("instance_of", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.InstanceOf)))
    }
  }

  case object InstanceArg extends JavascriptEdgePredicate("instance_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Instance)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArgOf, index)))
    }
  }

  case object ClassConstructor extends JavascriptEdgePredicate("class_constructor", forwardCost = 1, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassConstructor, name)))
    }
  }

  case object ClassMethod extends JavascriptEdgePredicate("class_method", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassMethod, name)))
    }
  }

  case object ClassDecorator extends JavascriptEdgePredicate("class_decorator", forwardCost = 2, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassDecorator)))
    }
  }

  case object ClassProperty extends JavascriptEdgePredicate("class_property", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Class)
    override val toImplicit = Some(JavascriptNodePredicate.ClassProperty)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ClassProperty, name)))
    }
  }

  case object ClassPropertyValue extends JavascriptEdgePredicate("class_property_value", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassProperty)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.ClassPropertyValue)))
    }
  }

  case object MethodArg extends JavascriptEdgePredicate("method_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index)))
    }
  }

  case object MethodDecorator extends JavascriptEdgePredicate("method_decorator", forwardCost = 2, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodDecorator)))
    }
  }

  @deprecated
  case object MethodContains extends JavascriptEdgePredicate("method_contains", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.ClassMethod)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }

    override val mustSpecifyNodes = true
  }

  case object FunctionArg extends JavascriptEdgePredicate("function_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)
    override val toImplicit = Some(JavascriptNodePredicate.FunctionArg)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.FunctionArgument, index)))
    }
  }

  @deprecated
  case object FunctionContains extends JavascriptEdgePredicate("function_contains", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Function)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }

    override val mustSpecifyNodes = true
  }

  case object Return extends JavascriptEdgePredicate("return", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Return)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Return)))
    }
  }

  case object Yield extends JavascriptEdgePredicate("yield", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Yield)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Yield)))
    }
  }

  case object Await extends JavascriptEdgePredicate("await", forwardCost = 1, reverseCost = 1) {

    override val fromImplicit = Some(JavascriptNodePredicate.Await)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Await)))
    }
  }

  case object Throw extends JavascriptEdgePredicate("throw", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.Throw)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.Throw)))
    }
  }

  case object Member extends JavascriptEdgePredicate("member", forwardCost = 1, reverseCost = 1) with HasName {
    override val toImplicit = Some(JavascriptNodePredicate.Member)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        *(JavascriptGraphEdgeType.follows.map(basic): _*),
        t(namedEdge(JavascriptGraphEdgeType.MemberOf, name)))
    }
  }

  case object Call extends JavascriptEdgePredicate("call", forwardCost = 1, reverseCost = 1) {
    override val toImplicit = Some(JavascriptNodePredicate.Call)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        *(JavascriptGraphEdgeType.follows.map(basic): _*),
        t(basic(JavascriptGraphEdgeType.CallOf)))
    }
  }

  case object CallArg extends JavascriptEdgePredicate("call_arg", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Call)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArgOf, index)))
    }
  }

  case object IfCondition extends JavascriptEdgePredicate("if_condition", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.IfBlock)),
        t(basic(JavascriptGraphEdgeType.IfTest)))
    }
  }

  case object IfBody extends JavascriptEdgePredicate("if_body", forwardCost = 1000, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.If)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.IfBlock)),
        t(basic(JavascriptGraphEdgeType.IfContains)))
    }

    override val mustSpecifyNodes = true
  }

  case object BinaryLeft extends JavascriptEdgePredicate("binary_left", forwardCost = 1, reverseCost = 1) with HasIndex {

    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.BasicExpression, Some(0))))
    }
  }

  case object BinaryRight extends JavascriptEdgePredicate("binary_right", forwardCost = 1, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.BinaryExpression)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.BasicExpression, Some(0))))
    }
  }

  case object UnaryExpression extends JavascriptEdgePredicate("unary_expression", forwardCost = 1, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.UnaryExpression)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.BasicExpression)))
    }
  }

  case object ArrayMember extends JavascriptEdgePredicate("array_member", forwardCost = 10, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.Array)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.ArrayMember, index)))
    }
  }

  case object ObjectProperty extends JavascriptEdgePredicate("object_property", forwardCost = 10, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.Object)
    override val toImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ObjectProperty, name)))
    }
  }

  case object ObjectPropertyValue extends JavascriptEdgePredicate("object_property_value", forwardCost = 1, reverseCost = 1) with HasName {
    override val fromImplicit = Some(JavascriptNodePredicate.ObjectProperty)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(namedEdge(JavascriptGraphEdgeType.ObjectValue, name)))
    }
  }

  case object TemplateComponent extends JavascriptEdgePredicate("template_component", forwardCost = 5, reverseCost = 1) with HasIndex {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateLiteral)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(indexedEdge(JavascriptGraphEdgeType.TemplateLiteral, index)))
    }
  }

  case object TemplateContains extends JavascriptEdgePredicate("template_contains", forwardCost = 100, reverseCost = 1) {
    override val fromImplicit = Some(JavascriptNodePredicate.TemplateExpression)

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.TemplateContains)))
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

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        ?(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)))
    }
  }

  case object CallLink extends JavascriptEdgePredicate("call_link", forwardCost = 1, reverseCost = 10) {
    override def fromImplicit = Some(JavascriptNodePredicate.Call)

    override def toImplicit = Some(NodePredicate.or(
      JavascriptNodePredicate.ClassMethod,
      JavascriptNodePredicate.Function))

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        t(basic(JavascriptGraphEdgeType.CallLink)))
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

    def queryTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty]): SrcLogTraverse = {
      lin(
        ?(basic(JavascriptGraphEdgeType.MethodFunction)),
        t(basic(JavascriptGraphEdgeType.FunctionContains)),
        t(basic(JavascriptGraphEdgeType.CallLink)))
    }
  }

}

// we only use reads here
object EdgePredicate extends Plenumeration[EdgePredicate] {
  override val all = {
    IndexType.all.flatMap(_.edgePredicate.all)
  }
}
