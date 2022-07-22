package models.index.ruby

import models.index.{ ValidEdgeConstraint, CanIndexConstraint, CanNameConstraint }
import silvousplay.imports._

/**
 * Graph restrictions
 */
sealed abstract class ValidEdge[From <: RubyNode, To <: RubyNode, Z <: RubyEdgeType] extends ValidEdgeConstraint[RubyNodeType, From, To, Z]

object ValidEdge {

  implicit object sendObject extends ValidEdge[SendNode, AnyNode, RubyEdgeType.SendObject.type]
  implicit object sendObjectNull extends ValidEdge[SendNode, SNullNode, RubyEdgeType.SendObject.type]
  implicit object sendArg extends ValidEdge[SendNode, AnyNode, RubyEdgeType.SendArg.type]

  implicit object pairKeySymbol extends ValidEdge[PairNode, SymNode, RubyEdgeType.PairKey.type]
  implicit object pairKeyString extends ValidEdge[PairNode, StrNode, RubyEdgeType.PairKey.type]
  implicit object pairValue extends ValidEdge[PairNode, AnyNode, RubyEdgeType.PairValue.type]

  implicit object arrayElement extends ValidEdge[ArrayNode, AnyNode, RubyEdgeType.ArrayElement.type]
  implicit object hashElement extends ValidEdge[HashNode, PairNode, RubyEdgeType.HashElement.type]

  implicit object index extends ValidEdge[IndexNode, AnyNode, RubyEdgeType.Index.type]

  implicit object const extends ValidEdge[ConstNode, ConstNode, RubyEdgeType.Const.type]
  implicit object constBase extends ValidEdge[ConstNode, CBaseNode, RubyEdgeType.Const.type]
  implicit object constNull extends ValidEdge[ConstNode, CNullNode, RubyEdgeType.Const.type]

  implicit object assignmentLocal extends ValidEdge[LVarNode, AnyNode, RubyEdgeType.Assignment.type]
  implicit object assignmentInstance extends ValidEdge[IVarNode, AnyNode, RubyEdgeType.Assignment.type]
  implicit object referenceLocal extends ValidEdge[ReferenceNode, LVarNode, RubyEdgeType.Reference.type]
  implicit object referenceInstance extends ValidEdge[ReferenceNode, IVarNode, RubyEdgeType.Reference.type]

  implicit object methodReturn extends ValidEdge[MethodNodeBuilder, ReturnNode, RubyEdgeType.MethodReturn.type]
  implicit object returnExp extends ValidEdge[ReturnNode, AnyNode, RubyEdgeType.Return.type]
  implicit object methodYield extends ValidEdge[MethodNodeBuilder, YieldNode, RubyEdgeType.MethodYield.type]
  implicit object yieldExp extends ValidEdge[YieldNode, AnyNode, RubyEdgeType.Yield.type]

  // control
  implicit object ensureTest extends ValidEdge[EnsureNode, AnyNode, RubyEdgeType.EnsureTest.type]
  implicit object ensure extends ValidEdge[EnsureNode, AnyNode, RubyEdgeType.Ensure.type]

  // loops
  implicit object untilCond extends ValidEdge[UntilNode, AnyNode, RubyEdgeType.LoopCondition.type]
  implicit object untilBody extends ValidEdge[UntilNode, AnyNode, RubyEdgeType.LoopBody.type]
  implicit object whileCond extends ValidEdge[WhileNode, AnyNode, RubyEdgeType.LoopCondition.type]
  implicit object whileBody extends ValidEdge[WhileNode, AnyNode, RubyEdgeType.LoopBody.type]

  // boolean
  implicit object lhsAnd extends ValidEdge[AndNode, AnyNode, RubyEdgeType.LHS.type]
  implicit object rhsAnd extends ValidEdge[AndNode, AnyNode, RubyEdgeType.RHS.type]
  implicit object lhsOr extends ValidEdge[OrNode, AnyNode, RubyEdgeType.LHS.type]
  implicit object rhsOr extends ValidEdge[OrNode, AnyNode, RubyEdgeType.RHS.type]
  implicit object lhsNot extends ValidEdge[NotNode, AnyNode, RubyEdgeType.LHS.type]
}

sealed trait CanIndex[T <: RubyEdgeType] extends CanIndexConstraint[T]

object CanIndex {
  implicit object SendArg extends CanIndex[RubyEdgeType.SendArg.type]

  implicit object ArrayElement extends CanIndex[RubyEdgeType.ArrayElement.type]
}

sealed trait CanName[T <: RubyEdgeType] extends CanNameConstraint[T]

object CanName {
  implicit object SendObject extends CanName[RubyEdgeType.SendObject.type]

  implicit object HashElement extends CanName[RubyEdgeType.HashElement.type]

  implicit object Const extends CanName[RubyEdgeType.Const.type]

  implicit object Index extends CanName[RubyEdgeType.Index.type]
}
