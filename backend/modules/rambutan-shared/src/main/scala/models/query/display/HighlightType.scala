package models.query.display

import models.query._
import models.query.grammar._
import silvousplay.imports._

sealed abstract class HighlightCategory(val identifier: String) extends Identifiable

object HighlightCategory extends Plenumeration[HighlightCategory] {
  case object Base extends HighlightCategory("base")
  case object Name extends HighlightCategory("name")
  case object Body extends HighlightCategory("body")

  case object Empty extends HighlightCategory("empty") // only multilineHighlight

  case object IndexOnly extends HighlightCategory("index-only")

  case object Arg extends HighlightCategory("arg")
  case object EmptyArg extends HighlightCategory("empty-arg")
  case object AddArg extends HighlightCategory("add-arg")
}

sealed abstract class HighlightColor(val identifier: String) extends Identifiable

object HighlightColor extends Plenumeration[HighlightColor] {
  case object Red extends HighlightColor("red")
  case object Blue extends HighlightColor("blue")
  case object Orange extends HighlightColor("orange")
  case object Green extends HighlightColor("green")
  case object White extends HighlightColor("white")
  case object Yellow extends HighlightColor("yellow")
  case object Gold extends HighlightColor("gold")
  case object Purple extends HighlightColor("purple")
}

/**
 * Core type
 */
sealed abstract class HighlightType(val identifier: String, val category: HighlightCategory) extends Identifiable {
  val parent: Option[EdgePredicate] = None
  val parser: Option[ParserType] = None
  val color: Option[HighlightColor] = None
}

sealed abstract class JavascriptHighlightBodyType[T <: ParserType with JavascriptParserType](identIn: String, val parentIn: JavascriptEdgePredicate, parserIn: T) extends HighlightType(identIn, HighlightCategory.Body) {
  override val parent = Some(parentIn)
  override val parser = Some(parserIn)
}

sealed abstract class RubyHighlightBodyType[T <: ParserType with RubyParserType](identIn: String, val parentIn: RubyEdgePredicate, parserIn: T) extends HighlightType(identIn, HighlightCategory.Body) {
  override val parent = Some(parentIn)
  override val parser = Some(parserIn)
}

sealed abstract class HighlightBaseType(identIn: String, colorIn: HighlightColor) extends HighlightType(identIn, HighlightCategory.Base) {
  override val color = Some(colorIn)
}

object HighlightType extends Plenumeration[HighlightType] {
  /**
   * Name
   */
  case object Name extends HighlightType("name", HighlightCategory.Name) {
    override val color = Some(HighlightColor.Green)
  }
  case object RedName extends HighlightType("red-name", HighlightCategory.Name) {
    override val color = Some(HighlightColor.Red)
  }
  case object WhiteName extends HighlightType("white-name", HighlightCategory.Name) {
    override val color = Some(HighlightColor.White)
  }
  case object YellowName extends HighlightType("yellow-name", HighlightCategory.Name) {
    override val color = Some(HighlightColor.Yellow)
  }

  /**
   * Base Type
   */
  case object BlueBase extends HighlightBaseType("blue-base", HighlightColor.Blue)
  case object GreenBase extends HighlightBaseType("green-base", HighlightColor.Green)
  case object OrangeBase extends HighlightBaseType("orange-base", HighlightColor.Orange)
  case object RedBase extends HighlightBaseType("red-base", HighlightColor.Red)
  case object WhiteBase extends HighlightBaseType("white-base", HighlightColor.White)
  case object GoldBase extends HighlightBaseType("gold-base", HighlightColor.Gold)
  case object PurpleBase extends HighlightBaseType("purple-base", HighlightColor.Purple)

  case object Arg extends HighlightType("arg", HighlightCategory.Arg) {
    override val color = Some(HighlightColor.Orange)
  }

  // index nubbin
  case object IndexOnly extends HighlightType("index-only", HighlightCategory.IndexOnly)

  case object Empty extends HighlightType("empty", HighlightCategory.Empty)

  /**
   * Universal body types
   * - These are not type checked
   */
  case object Any extends HighlightType("any", HighlightCategory.Body) {
    override val parser = Some(ParserType.Default)
  }

  case object Traverse extends HighlightType("traverse", HighlightCategory.Body) {
    override val parser = Some(ParserType.Traverse)
  }

}

object JavascriptHighlightType extends Plenumeration[HighlightType] {

  case object ClassExtends extends JavascriptHighlightBodyType(
    "class-extends",
    JavascriptEdgePredicate.ClassExtends,
    JavascriptParserType.RootExpression)

  case object ClassBody extends JavascriptHighlightBodyType(
    "class-body",
    JavascriptEdgePredicate.FunctionContains, //ignored
    JavascriptParserType.ClassBody)

  case object ClassPropertyValue extends JavascriptHighlightBodyType(
    "class-property-value",
    JavascriptEdgePredicate.ClassPropertyValue,
    JavascriptParserType.BasicExpression)

  case object InstanceOf extends JavascriptHighlightBodyType(
    "instance-of",
    JavascriptEdgePredicate.InstanceOf,
    JavascriptParserType.RootExpression)

  case object InstanceArg extends JavascriptHighlightBodyType(
    "instance-arg",
    JavascriptEdgePredicate.InstanceArg,
    JavascriptParserType.FunctionBody)

  case object FunctionBody extends JavascriptHighlightBodyType(
    "function-body",
    JavascriptEdgePredicate.FunctionContains,
    JavascriptParserType.FunctionBody)

  case object FunctionReturn extends JavascriptHighlightBodyType(
    "function-return",
    JavascriptEdgePredicate.Return,
    JavascriptParserType.FunctionBody)

  case object FunctionYield extends JavascriptHighlightBodyType(
    "function-yield",
    JavascriptEdgePredicate.Yield,
    JavascriptParserType.FunctionBody)

  case object FunctionThrow extends JavascriptHighlightBodyType(
    "function-throw",
    JavascriptEdgePredicate.Throw,
    JavascriptParserType.FunctionBody)

  case object FunctionAwait extends JavascriptHighlightBodyType(
    "function-await",
    JavascriptEdgePredicate.Await,
    JavascriptParserType.FunctionBody)

  case object MethodBody extends JavascriptHighlightBodyType(
    "method-body",
    JavascriptEdgePredicate.MethodContains,
    JavascriptParserType.FunctionBody)

  case object IfCondition extends JavascriptHighlightBodyType(
    "if-condition",
    JavascriptEdgePredicate.IfCondition,
    JavascriptParserType.BasicExpression)

  case object IfBody extends JavascriptHighlightBodyType(
    "if-body",
    JavascriptEdgePredicate.IfBody,
    JavascriptParserType.FunctionBody)

  case object CallArg extends JavascriptHighlightBodyType(
    "call-arg",
    JavascriptEdgePredicate.CallArg,
    JavascriptParserType.FunctionBody)

  case object JSXTag extends JavascriptHighlightBodyType(
    "jsx-tag",
    JavascriptEdgePredicate.JSXTag,
    JavascriptParserType.RootExpression)

  case object JSXAttribute extends JavascriptHighlightBodyType(
    "jsx-attr",
    JavascriptEdgePredicate.JSXAttribute,
    JavascriptParserType.JSXAttribute)

  case object JSXAttributeValue extends JavascriptHighlightBodyType(
    "jsx-value",
    JavascriptEdgePredicate.JSXAttributeValue,
    JavascriptParserType.FunctionBody)

  case object JSXChild extends JavascriptHighlightBodyType(
    "jsx-child",
    JavascriptEdgePredicate.JSXChild,
    JavascriptParserType.FunctionBody)

  case object MemberStart extends JavascriptHighlightBodyType(
    "member-start",
    JavascriptEdgePredicate.Member,
    JavascriptParserType.RootExpression)

  case object CallStart extends JavascriptHighlightBodyType(
    "call-start",
    JavascriptEdgePredicate.Call,
    JavascriptParserType.RootExpression)

  case object BinaryLeft extends JavascriptHighlightBodyType(
    "binary-left",
    JavascriptEdgePredicate.BinaryLeft,
    JavascriptParserType.BasicExpression)

  case object BinaryRight extends JavascriptHighlightBodyType(
    "binary-right",
    JavascriptEdgePredicate.BinaryRight,
    JavascriptParserType.BasicExpression)

  case object UnaryBody extends JavascriptHighlightBodyType(
    "unary-body",
    JavascriptEdgePredicate.UnaryExpression,
    JavascriptParserType.BasicExpression)

  case object ArrayMember extends JavascriptHighlightBodyType(
    "array-member",
    JavascriptEdgePredicate.ArrayMember,
    JavascriptParserType.FunctionBody)

  case object ObjectProperty extends JavascriptHighlightBodyType(
    "object-property",
    JavascriptEdgePredicate.ObjectProperty,
    JavascriptParserType.ObjectProperty)

  case object ObjectPropertyValue extends JavascriptHighlightBodyType(
    "object-property-value",
    JavascriptEdgePredicate.ObjectPropertyValue,
    JavascriptParserType.FunctionBody)

  case object FunctionContainsTraverse extends JavascriptHighlightBodyType(
    "function-traverse",
    JavascriptEdgePredicate.FunctionContains,
    JavascriptParserType.Traverse)

  case object MethodContainsTraverse extends JavascriptHighlightBodyType(
    "method-traverse",
    JavascriptEdgePredicate.MethodContains,
    JavascriptParserType.Traverse)

  /**
   * Arg types
   */
  case object EmptyFunctionArg extends HighlightType("empty-function-arg", HighlightCategory.EmptyArg) {
    override val parent = Some(JavascriptEdgePredicate.FunctionArg)
  }
  case object EmptyMethodArg extends HighlightType("empty-method-arg", HighlightCategory.EmptyArg) {
    override val parent = Some(JavascriptEdgePredicate.MethodArg)
  }

}

object RubyHighlightType extends Plenumeration[HighlightType] {

  case object SendArg extends RubyHighlightBodyType(
    "send-arg",
    RubyEdgePredicate.SendArg,
    RubyParserType.Default)

  case object ArrayElement extends RubyHighlightBodyType(
    "array-element",
    RubyEdgePredicate.ArrayElement,
    RubyParserType.Default)

  case object HashElement extends RubyHighlightBodyType(
    "hash-element",
    RubyEdgePredicate.HashElement,
    RubyParserType.HashElement)

  case object PairValue extends RubyHighlightBodyType(
    "pair-value",
    RubyEdgePredicate.PairValue,
    RubyParserType.Default)

  // dynamic traverses
  case object ConstTraverse extends HighlightType("const-traverse", HighlightCategory.Body) {
    override val parser = Some(RubyParserType.ConstTraverse)
  }

  case object TraverseWithArg extends HighlightType("traverse-with-arg", HighlightCategory.Body) {
    override val parser = Some(RubyParserType.TraverseWithArg)
  }
}
