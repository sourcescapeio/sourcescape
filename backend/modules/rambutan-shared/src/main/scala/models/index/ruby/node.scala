package models.index.ruby

import models.index._
import models.{ CodeRange }
import silvousplay.imports._
import play.api.libs.json._
import models.index.esprima.ESPrimaTag

sealed trait RubyNode extends GraphNodeData[RubyNodeType] {
  // def id: String
  // def nodeType: ESPrimaNodeType
}

sealed abstract class RubyTag(val identifier: String) extends Identifiable

sealed abstract class RubyNodeBuilder(
  val nodeType:    RubyNodeType,
  val graphName:   Option[String] = None,
  additionalNames: List[String]   = Nil,
  graphIndex:      Option[Int]    = None) extends RubyNode with StandardNodeBuilder[RubyNodeType, RubyTag] {

  // TODO: linking stubbed out right now
  def lookupIndex: Option[Int] = None
  def definitionLink: Option[String] = None
  def typeDefinitionLink: Option[String] = None

  def generateSymbol: Boolean = false

  val names = graphName.toList ++ additionalNames

  val index = graphIndex

  val tags: List[RubyTag] = Nil
}

/**
 * Impl
 */
// Literals
sealed abstract class LiteralBuilder(nt: RubyNodeType, val name: String) extends RubyNodeBuilder(nt, graphName = Some(name))
case class IntNode(id: String, range: CodeRange, value: Int) extends LiteralBuilder(RubyNodeType.NumberLiteral, value.toString)
case class FloatNode(id: String, range: CodeRange, value: Double) extends LiteralBuilder(RubyNodeType.NumberLiteral, value.toString)
// these types are simplified to make querying easier
case class KeywordLiteralNode(id: String, range: CodeRange, value: String) extends LiteralBuilder(RubyNodeType.KeywordLiteral, value)
case class StrNode(id: String, range: CodeRange, value: String) extends LiteralBuilder(RubyNodeType.Str, value)
case class SymNode(id: String, range: CodeRange, value: String) extends LiteralBuilder(RubyNodeType.Sym, value)

// Blocks
case class BeginNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Begin)

// Vars
case class ConstNode(id: String, range: CodeRange, name: String) extends RubyNodeBuilder(RubyNodeType.Const, graphName = Some(name))
case class CBaseNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.CBase)
case class CNullNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.CNull)

case class SendNode(id: String, range: CodeRange, operator: String) extends RubyNodeBuilder(RubyNodeType.Send, graphName = Some(operator))
case class SNullNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.SNull)

// Loop
case class ForNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.For)
case class BreakNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Break)
case class NextNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Next)
case class RedoNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Redo)
case class WhileNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.While)
case class UntilNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Until)

// data
case class PairNode(id: String, range: CodeRange, name: Option[String]) extends RubyNodeBuilder(RubyNodeType.Pair, graphName = name)
case class HashNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Hash)
case class ArrayNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Array)
case class IndexNode(id: String, range: CodeRange, name: String) extends RubyNodeBuilder(RubyNodeType.Send, graphName = Some(name))
case class RangeNode(id: String, range: CodeRange, start: Int, end: Option[Int], exclusive: Boolean) extends RubyNodeBuilder(RubyNodeType.Range)

case class DStrNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.DStr)

// Class blocks
case class ClassNode(id: String, range: CodeRange, name: Option[String]) extends RubyNodeBuilder(RubyNodeType.Class, graphName = name)
case class ModuleNode(id: String, range: CodeRange, name: Option[String]) extends RubyNodeBuilder(RubyNodeType.Module, graphName = name)
case class SelfNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Self)
case class SuperNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Super)

// Method stuff
sealed abstract class MethodNodeBuilder(nt: RubyNodeType) extends RubyNodeBuilder(nt)
case class DefNode(id: String, range: CodeRange, name: String) extends MethodNodeBuilder(RubyNodeType.Def)
case class BlockNode(id: String, range: CodeRange) extends MethodNodeBuilder(RubyNodeType.Block)
case class ReturnNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Return)
case class YieldNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Yield)
case class BlockPassNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.BlockPass)

// control
case class RescueNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Rescue)
case class EnsureNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Ensure)

// Boolean
case class CaseNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Case)
case class IfNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.If)
case class AndNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.And)
case class OrNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Or)
case class NotNode(id: String, range: CodeRange) extends RubyNodeBuilder(RubyNodeType.Not)

// References
case class IVarNode(id: String, range: CodeRange, name: String) extends RubyNodeBuilder(RubyNodeType.IVar, graphName = Some(name))
case class LVarNode(id: String, range: CodeRange, name: String) extends RubyNodeBuilder(RubyNodeType.LVar, graphName = Some(name))
case class ReferenceNode(id: String, range: CodeRange, name: String) extends RubyNodeBuilder(RubyNodeType.Reference, graphName = Some(name))

// just a container for id
case class AnyNode(node: RubyNodeBuilder) extends RubyNode {
  val id = node.id
  val nodeType = node.nodeType
}
