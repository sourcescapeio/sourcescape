package models.query.grammar

import silvousplay.imports._
import models.query._
import play.api.libs.json._

sealed abstract class PayloadType(val identifier: String) extends Identifiable {
  val nodePredicates = Map.empty[String, NodePredicate]
  val edgePredicates = Map.empty[String, EdgePredicate]

  def allPredicates = nodePredicates.map {
    case (k, v) => k -> v.identifier
  } ++ edgePredicates.map {
    case (k, v) => k -> v.identifier
  }

  val defaultAdditional = Json.obj()

  def toJson(additional: JsObject) = {
    Json.obj(
      "type" -> identifier,
      "predicates" -> allPredicates) ++ defaultAdditional ++ additional
  }
}

object BasePayloadType extends Plenumeration[PayloadType] {
  case object Ref extends PayloadType("REF")
}

object JavascriptPayloadType extends Plenumeration[PayloadType] {
  // deps are likely language specific
  case object Dep extends PayloadType("DEP") {
    override val edgePredicates = Map(
      "edge" -> UniversalEdgePredicate.RequireDependency)
  }

  case object Super extends PayloadType("SUPER") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Super)
  }

  case object This extends PayloadType("THIS") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.This)
  }

  case object Instance extends PayloadType("INSTANCE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Instance)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.InstanceOf,
      "arg" -> JavascriptEdgePredicate.InstanceArg)
  }

  case object ClassDefinition extends PayloadType("CLASS_DEFINITION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Class)

    override val edgePredicates = Map(
      "extends" -> JavascriptEdgePredicate.ClassExtends)

  }

  case object ClassProperty extends PayloadType("CLASS_PROPERTY") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.ClassProperty)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.ClassProperty,
      "value" -> JavascriptEdgePredicate.ClassPropertyValue)
  }

  case object MethodDefinition extends PayloadType("METHOD_DEFINITION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.ClassMethod)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.ClassMethod,
      "arg" -> JavascriptEdgePredicate.MethodArg)
  }

  case object ArrayExpression extends PayloadType("ARRAY_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Array)

    override val edgePredicates = Map(
      "member" -> JavascriptEdgePredicate.ArrayMember)
  }

  case object ObjectExpression extends PayloadType("OBJECT_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Object)

    override val edgePredicates = Map(
      "prop" -> JavascriptEdgePredicate.ObjectProperty)
  }

  case object ObjectAttribute extends PayloadType("OBJECT_ATTRIBUTE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.ObjectProperty)

    override val edgePredicates = Map(
      "value" -> JavascriptEdgePredicate.ObjectPropertyValue)
  }

  case object FunctionDefinition extends PayloadType("FUNCTION_DEFINITION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Function)

    override val edgePredicates = Map(
      "arg" -> JavascriptEdgePredicate.FunctionArg)
  }

  // returns
  case object ReturnStatement extends PayloadType("RETURN_STATEMENT") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Return)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Return)

    override val defaultAdditional = Json.obj(
      "predicate" -> "return")
  }

  case object YieldStatement extends PayloadType("YIELD_STATEMENT") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Yield)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Yield)

    override val defaultAdditional = Json.obj(
      "predicate" -> "yield")
  }

  case object AwaitStatement extends PayloadType("AWAIT_STATEMENT") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Await)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Await)

    override val defaultAdditional = Json.obj(
      "predicate" -> "await")
  }

  case object ThrowStatement extends PayloadType("THROW_STATEMENT") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Throw)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Throw)

    override val defaultAdditional = Json.obj(
      "predicate" -> "throw")
  }

  //
  case object Identifier extends PayloadType("IDENTIFIER") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Identifier)
  }

  // jsx
  case object JSXAttribute extends PayloadType("JSX_ATTRIBUTE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.JSXAttribute)

    override val edgePredicates = Map(
      "value" -> JavascriptEdgePredicate.JSXAttributeValue)
  }

  case object JSXExpression extends PayloadType("JSX_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.JSXElement)

    override val edgePredicates = Map(
      "tag" -> JavascriptEdgePredicate.JSXTag,
      "attr" -> JavascriptEdgePredicate.JSXAttribute)
  }

  // literals
  case object Keyword extends PayloadType("KEYWORD") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Literal)
  }

  case object Number extends PayloadType("NUMBER") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Literal)
  }

  case object String extends PayloadType("STRING") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.LiteralString)
  }

  // cond
  case object UnaryExpression extends PayloadType("UNARY_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.UnaryExpression)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.UnaryExpression)
  }

  case object BinaryExpression extends PayloadType("BINARY_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.BinaryExpression)

    override val edgePredicates = Map(
      "left" -> JavascriptEdgePredicate.BinaryLeft,
      "right" -> JavascriptEdgePredicate.BinaryRight)
  }

  case object IfStatement extends PayloadType("IF_STATEMENT") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.If)

    override val edgePredicates = Map(
      "cond" -> JavascriptEdgePredicate.IfCondition)
  }

  // exp
  case object Require extends PayloadType("REQUIRE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Require)
  }

  case object TemplateComponent extends PayloadType("TEMPLATE_COMPONENT") {
    override val nodePredicates = Map(
      "literal" -> JavascriptNodePredicate.TemplateLiteral)
  }

  case object Template extends PayloadType("TEMPLATE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.TemplateExpression)

    override val edgePredicates = Map(
      "component" -> JavascriptEdgePredicate.TemplateComponent,
      "contains" -> JavascriptEdgePredicate.TemplateContains)
  }

  // traverses
  case object CallTraverse extends PayloadType("CALL_TRAVERSE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Call)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Call,
      "arg" -> JavascriptEdgePredicate.CallArg)

  }

  case object MemberTraverse extends PayloadType("MEMBER_TRAVERSE") {
    override val nodePredicates = Map(
      "node" -> JavascriptNodePredicate.Member)

    override val edgePredicates = Map(
      "edge" -> JavascriptEdgePredicate.Member)
  }

  case object TraverseList extends PayloadType("TRAVERSE_LIST")

  case object RootedExpression extends PayloadType("ROOTED_EXPRESSION")

}

object RubyPayloadType extends Plenumeration[PayloadType] {

  case object Identifier extends PayloadType("IDENTIFIER") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.Const)
  }

  // traverses
  case object Const extends PayloadType("CONST") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.Const,
      "cbase" -> RubyNodePredicate.CBase,
      "cnull" -> RubyNodePredicate.CNull)

    override val edgePredicates = Map(
      "edge" -> RubyEdgePredicate.Const)
  }

  // case object ConstRoot extends PayloadType("CONST_ROOT") {
  //   override val nodePredicates = Map(
  //     "node" -> RubyNodePredicate.Const,
  //     "null" -> RubyNodePredicate.CNull)

  //   override val edgePredicates = Map(
  //     "edge" -> RubyEdgePredicate.Send)
  // }

  case object SendRoot extends PayloadType("SEND_ROOT") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.Send,
      "snull" -> RubyNodePredicate.SNull)

    override val edgePredicates = Map(
      "edge" -> RubyEdgePredicate.Send,
      "arg" -> RubyEdgePredicate.SendArg)
  }

  case object RootedExpression extends PayloadType("ROOTED_EXPRESSION") {
    override val edgePredicates = Map(
      "edge" -> RubyEdgePredicate.Send,
      "arg" -> RubyEdgePredicate.SendArg)
  }

  case object AppendArg extends PayloadType("APPEND_ARG") {
    override val edgePredicates = Map(
      "arg" -> RubyEdgePredicate.SendArg)
  }

  case object TraverseList extends PayloadType("TRAVERSE_LIST") {
    override val edgePredicates = Map(
      "const" -> RubyEdgePredicate.Const,
      "edge" -> RubyEdgePredicate.Send,
      "arg" -> RubyEdgePredicate.SendArg)
  }

  // data
  case object ArrayExpression extends PayloadType("ARRAY_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.Array)

    override val edgePredicates = Map(
      "element" -> RubyEdgePredicate.ArrayElement) // TODO: fix
  }

  case object HashExpression extends PayloadType("HASH_EXPRESSION") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.Hash)

    override val edgePredicates = Map(
      "prop" -> RubyEdgePredicate.HashElement)
  }

  case object HashPair extends PayloadType("HASH_PAIR") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.HashPair)

    override val edgePredicates = Map(
      "value" -> RubyEdgePredicate.PairValue)
  }

  // literals
  case object KeywordLiteral extends PayloadType("KEYWORD") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.KeywordLiteral)
  }

  case object NumberLiteral extends PayloadType("NUMBER") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.NumberLiteral)
  }

  case object StringLiteral extends PayloadType("STRING") {
    override val nodePredicates = Map(
      "node" -> RubyNodePredicate.StringLiteral)
  }

}