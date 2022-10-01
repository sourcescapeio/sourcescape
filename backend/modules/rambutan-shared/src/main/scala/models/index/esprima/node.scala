package models.index.esprima

import models.index._
import models.{ CodeRange }
import silvousplay.imports._
import play.api.libs.json._

sealed trait ESPrimaNode extends GraphNodeData[ESPrimaNodeType] {
  // def id: String
  // def nodeType: ESPrimaNodeType
}

sealed abstract class ESPrimaTag(val identifier: String) extends Identifiable

sealed abstract class ESPrimaNodeBuilder(
  val nodeType:    ESPrimaNodeType,
  graphName:       Option[String]  = None,
  additionalNames: List[String]    = Nil,
  graphIndex:      Option[Int]     = None) extends ESPrimaNode with StandardNodeBuilder[ESPrimaNodeType, ESPrimaTag] {

  val names = graphName.toList ++ additionalNames

  val index = graphIndex

  val tags: List[ESPrimaTag] = Nil
}

/**
 * Impl
 */
case class RequireNode(id: String, range: CodeRange, module: String, searches: List[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Require, graphName = Some(module), additionalNames = searches)

// can be discarded if identifier already exists
case class IdentifierNode(id: String, range: CodeRange, name: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.Identifier, graphName = Some(name))

case class IdentifierReferenceNode(id: String, range: CodeRange, name: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.IdentifierRef, graphName = Some(name)) {
  def toIdentifier = IdentifierNode(id, range, name)
}

case class InstantiationNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Instance)

case class MemberNode(id: String, range: CodeRange, name: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Member, graphName = name)

case class CallNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Call)

case class LiteralNode(id: String, range: CodeRange, raw: String, value: JsValue) extends ESPrimaNodeBuilder(ESPrimaNodeType.Literal, graphName = Some(Json.stringify(value)))

case class ArrayNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Array)

// Ex: left || right
// Will be connected with ArgumentEdges with LogicalExpression edge type
case class BinaryExpressionNode(id: String, range: CodeRange, operator: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.BinaryExp, graphName = Some(operator))

// todo: do something with prefix
case class UnaryExpressionNode(id: String, range: CodeRange, operator: String, prefix: Boolean) extends ESPrimaNodeBuilder(ESPrimaNodeType.UnaryExp, graphName = Some(operator))

/**
 * Loops
 */

case class WhileNode(id: String, range: CodeRange, doWhile: Boolean) extends ESPrimaNodeBuilder(ESPrimaNodeType.While)

case class ForNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.For)
case class LoopVarNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.LoopVar)

/**
 * Control flow
 */
// If statements
// If node is the top level
// - contains multiple if blocks
case class IfNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.If)
case class IfBlockNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.IfBlock)
case class ConditionalNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Conditional)

// Switch
case class SwitchNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Switch)
case class SwitchBlockNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.SwitchBlock)
case class BreakNode(id: String, range: CodeRange, label: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Break, graphName = label)
case class ContinueNode(id: String, range: CodeRange, label: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Continue, graphName = label)

case class WithNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.With)

// Try
case class TryNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Try)
case class CatchNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Catch)
case class CatchArgNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.CatchArg)
case class FinallyNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Finally)

// Throw
case class ThrowNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Throw)

// Ex: class
case class ClassNode(id: String, range: CodeRange, name: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Class, graphName = name)
case class MethodNode(id: String, range: CodeRange, computed: Boolean, static: Boolean, constructor: Boolean, name: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Method, graphName = name)
case class ClassPropertyNode(id: String, range: CodeRange, computed: Boolean, static: Boolean, name: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.ClassProperty, graphName = name)
case class SuperNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Super)
case class ThisNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.This)
case class DecoratorNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Decorator)

// Ex: { a: 1, b: 2}
case class ObjectNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Object)

// Ex: a: 1
case class ObjectPropertyNode(id: String, range: CodeRange, key: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.ObjectProp, graphName = Some(key))

/**
 * Function shit
 */
// Ex: function() { }
case class FunctionNode(id: String, range: CodeRange, async: Boolean, name: Option[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Function, graphName = name)
case class SpreadNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Spread)

// HMMM on index
case class FunctionArgNode(id: String, range: CodeRange, indexIn: Int) extends ESPrimaNodeBuilder(ESPrimaNodeType.FunctionArg, graphIndex = Some(indexIn))

case class YieldNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Yield)

case class AwaitNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Await)

// Ex: return 1;
case class ReturnNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Return)

// Ex: module.exports = { }
case class ExportNode(id: String, range: CodeRange, path: String, additionalPaths: List[String]) extends ESPrimaNodeBuilder(ESPrimaNodeType.Export, graphName = Some(path), additionalNames = additionalPaths)

case class ExportKeyNode(id: String, range: CodeRange, name: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.ExportKey, graphName = Some(name))

case class LabelNode(id: String, range: CodeRange, label: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.Label, graphName = Some(label))

case class SequenceNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.Sequence)

case class JSXElementNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.JSXElement)
case class JSXAttributeNode(id: String, range: CodeRange, name: String) extends ESPrimaNodeBuilder(ESPrimaNodeType.JSXAttribute, graphName = Some(name))

case class TemplateLiteralNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.TemplateLiteral)

case class TemplateExpressionNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.TemplateExpression)

case class TaggedTemplateNode(id: String, range: CodeRange) extends ESPrimaNodeBuilder(ESPrimaNodeType.TaggedTemplate)

// just a container for id
case class AnyNode(node: ESPrimaNodeBuilder) extends ESPrimaNode {
  val id = node.id
  val nodeType = node.nodeType
}
