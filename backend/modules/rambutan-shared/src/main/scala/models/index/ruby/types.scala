package models.index.ruby

import models.index.{ EdgeType, NodeType }
import silvousplay.imports._

/**
 * Nodes
 */
sealed abstract class RubyNodeType(val identifier: String) extends NodeType

object RubyNodeType extends Plenumeration[RubyNodeType] {

  case object Begin extends RubyNodeType("begin")

  // Lits
  case object Str extends RubyNodeType("str")
  case object Sym extends RubyNodeType("sym")

  // Simplified lits
  case object NumberLiteral extends RubyNodeType("number")
  case object KeywordLiteral extends RubyNodeType("keyword-literal") // covers bool, nil etc.

  case object Pair extends RubyNodeType("pair")
  case object Hash extends RubyNodeType("hash")

  case object DStr extends RubyNodeType("dstr")

  // Arrays
  case object Array extends RubyNodeType("array")
  case object Range extends RubyNodeType("range")

  // Loops
  case object For extends RubyNodeType("for")
  case object Break extends RubyNodeType("break")
  case object Next extends RubyNodeType("next")
  case object Redo extends RubyNodeType("redo")
  case object While extends RubyNodeType("while")
  case object Until extends RubyNodeType("until")

  // control
  case object Rescue extends RubyNodeType("rescue")
  case object Ensure extends RubyNodeType("ensure")

  // Rooted
  case object Const extends RubyNodeType("const")
  case object CBase extends RubyNodeType("cbase")
  case object CNull extends RubyNodeType("cnull") // this denotes a null value for the const root, unscoped constant

  case object Send extends RubyNodeType("send")
  case object SNull extends RubyNodeType("snull")

  case object Index extends RubyNodeType("index")

  // class stuff
  case object Class extends RubyNodeType("class")
  case object Module extends RubyNodeType("module")
  case object Self extends RubyNodeType("self")
  case object Super extends RubyNodeType("super")

  // method stuff
  case object Def extends RubyNodeType("def")
  case object Block extends RubyNodeType("block")
  case object Return extends RubyNodeType("return")
  case object Yield extends RubyNodeType("yield")
  case object BlockPass extends RubyNodeType("block_pass")

  // boolean stuff
  case object Case extends RubyNodeType("case")
  case object If extends RubyNodeType("if")
  case object And extends RubyNodeType("and")
  case object Or extends RubyNodeType("or")
  case object Not extends RubyNodeType("not")

  // references
  case object Reference extends RubyNodeType("reference")
  case object LVar extends RubyNodeType("lvar")
  case object IVar extends RubyNodeType("ivar")
}

sealed abstract class RubyEdgeType(val identifier: String) extends EdgeType

/**
 * Edges
 */
object RubyEdgeType extends Plenumeration[RubyEdgeType] {
  case object SendObject extends RubyEdgeType("send-object")
  case object SendArg extends RubyEdgeType("send-arg")

  case object Const extends RubyEdgeType("const")

  case object PairKey extends RubyEdgeType("pair-key")
  case object PairValue extends RubyEdgeType("pair-value")
  case object Hash extends RubyEdgeType("hash")

  case object Index extends RubyEdgeType("index")

  case object ArrayElement extends RubyEdgeType("array-element")
  case object HashElement extends RubyEdgeType("hash-element")

  // control
  case object EnsureTest extends RubyEdgeType("ensure-test")
  case object Ensure extends RubyEdgeType("ensure")

  // loops
  case object LoopCondition extends RubyEdgeType("loop-condition")
  case object LoopBody extends RubyEdgeType("loop-body")

  // methods
  case object Return extends RubyEdgeType("return")
  case object MethodReturn extends RubyEdgeType("method-return")
  case object Yield extends RubyEdgeType("yield")
  case object MethodYield extends RubyEdgeType("method-yield")

  // references
  case object Assignment extends RubyEdgeType("assignment")
  case object Reference extends RubyEdgeType("reference")

  // boolean
  case object LHS extends RubyEdgeType("lhs")
  case object RHS extends RubyEdgeType("rhs")
}
