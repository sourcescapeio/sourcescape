package models.index.esprima

import models.index.{ ValidEdgeConstraint, CanIndexConstraint, CanNameConstraint }
import silvousplay.imports._

/**
 * Graph restrictions
 */
sealed abstract class ValidEdge[From <: ESPrimaNode, To <: ESPrimaNode, Z <: ESPrimaEdgeType] extends ValidEdgeConstraint[ESPrimaNodeType, From, To, Z]

object ValidEdge {
  /**
   * Variables
   */
  // Declare
  implicit object declareMember extends ValidEdge[IdentifierNode, MemberNode, ESPrimaEdgeType.Declare.type]
  implicit object declareFunction extends ValidEdge[IdentifierNode, FunctionNode, ESPrimaEdgeType.Declare.type]
  implicit object declareRequire extends ValidEdge[IdentifierNode, RequireNode, ESPrimaEdgeType.Declare.type]
  implicit object declareClass extends ValidEdge[IdentifierNode, ClassNode, ESPrimaEdgeType.Declare.type]
  implicit object declareAny extends ValidEdge[IdentifierNode, AnyNode, ESPrimaEdgeType.Declare.type] // for vars

  // Assignment
  // from = left
  // to = right
  implicit object assignment extends ValidEdge[AnyNode, AnyNode, ESPrimaEdgeType.Assignment.type]

  /**
   * Classes
   */
  // Class
  implicit object instanceEdge extends ValidEdge[InstantiationNode, AnyNode, ESPrimaEdgeType.Class.type]

  // Class definition
  implicit object classMethod extends ValidEdge[ClassNode, MethodNode, ESPrimaEdgeType.Method.type]
  implicit object classConstructor extends ValidEdge[ClassNode, MethodNode, ESPrimaEdgeType.Constructor.type]
  implicit object superClass extends ValidEdge[ClassNode, AnyNode, ESPrimaEdgeType.SuperClass.type]
  implicit object methodKey extends ValidEdge[MethodNode, AnyNode, ESPrimaEdgeType.MethodKey.type]
  implicit object methodFunction extends ValidEdge[MethodNode, FunctionNode, ESPrimaEdgeType.MethodFunction.type]
  implicit object classProperty extends ValidEdge[ClassNode, ClassPropertyNode, ESPrimaEdgeType.ClassProperty.type]
  implicit object classPropertyKey extends ValidEdge[ClassPropertyNode, AnyNode, ESPrimaEdgeType.ClassPropertyKey.type]
  implicit object classPropertyValue extends ValidEdge[ClassPropertyNode, AnyNode, ESPrimaEdgeType.ClassPropertyValue.type]
  implicit object classDecorator extends ValidEdge[ClassNode, AnyNode, ESPrimaEdgeType.ClassDecorator.type]
  implicit object methodDecorator extends ValidEdge[MethodNode, AnyNode, ESPrimaEdgeType.MethodDecorator.type]

  // Reference
  implicit object reference extends ValidEdge[IdentifierReferenceNode, IdentifierNode, ESPrimaEdgeType.Reference.type]

  /**
   * Functions
   */
  // Function
  implicit object functionArgument extends ValidEdge[FunctionNode, FunctionArgNode, ESPrimaEdgeType.FunctionArgument.type]
  implicit object functionReturn extends ValidEdge[ReturnNode, FunctionNode, ESPrimaEdgeType.FunctionReturn.type]
  // implicit object functionExpReturn extends ValidEdge[AnyNode, FunctionNode, ESPrimaEdgeType.FunctionReturn.type]
  implicit object functionYield extends ValidEdge[YieldNode, FunctionNode, ESPrimaEdgeType.FunctionReturn.type]
  implicit object functionAwait extends ValidEdge[AwaitNode, FunctionNode, ESPrimaEdgeType.FunctionReturn.type]
  implicit object functionContains extends ValidEdge[FunctionNode, AnyNode, ESPrimaEdgeType.FunctionContains.type]

  implicit object yieldEdge extends ValidEdge[YieldNode, AnyNode, ESPrimaEdgeType.Yield.type]
  implicit object awaitEdge extends ValidEdge[AwaitNode, AnyNode, ESPrimaEdgeType.Await.type]
  implicit object returnAny extends ValidEdge[ReturnNode, AnyNode, ESPrimaEdgeType.Return.type]
  implicit object returnContains extends ValidEdge[ReturnNode, AnyNode, ESPrimaEdgeType.ReturnContains.type]

  // Argument
  implicit object argument extends ValidEdge[AnyNode, CallNode, ESPrimaEdgeType.Argument.type]
  implicit object newArgument extends ValidEdge[AnyNode, InstantiationNode, ESPrimaEdgeType.Argument.type]
  implicit object spread extends ValidEdge[SpreadNode, AnyNode, ESPrimaEdgeType.Spread.type]

  // Return

  // Call
  implicit object call extends ValidEdge[CallNode, AnyNode, ESPrimaEdgeType.Call.type]

  /**
   * Require
   */
  // Export
  implicit object export extends ValidEdge[AnyNode, ExportNode, ESPrimaEdgeType.Export.type]
  implicit object exportKey extends ValidEdge[AnyNode, ExportKeyNode, ESPrimaEdgeType.Export.type]
  implicit object exportLink extends ValidEdge[ExportKeyNode, ExportNode, ESPrimaEdgeType.ExportKey.type]

  // Link
  // implicit object link extends ValidEdge[RequireNode, ExportNode, ESPrimaEdgeType.Link.type]

  /**
   * Loops
   */
  implicit object whileContains extends ValidEdge[WhileNode, AnyNode, ESPrimaEdgeType.WhileContains.type]
  implicit object whileTest extends ValidEdge[WhileNode, AnyNode, ESPrimaEdgeType.WhileTest.type]

  implicit object forContains extends ValidEdge[ForNode, AnyNode, ESPrimaEdgeType.ForContains.type]
  implicit object forInit extends ValidEdge[LoopVarNode, AnyNode, ESPrimaEdgeType.ForInit.type]
  implicit object forVar extends ValidEdge[ForNode, LoopVarNode, ESPrimaEdgeType.ForVar.type]

  implicit object forTest extends ValidEdge[ForNode, AnyNode, ESPrimaEdgeType.ForTest.type]
  implicit object forUpdate extends ValidEdge[ForNode, AnyNode, ESPrimaEdgeType.ForUpdate.type]

  /**
   * Control
   */
  implicit object withExp extends ValidEdge[WithNode, AnyNode, ESPrimaEdgeType.With.type]
  implicit object withContains extends ValidEdge[WithNode, AnyNode, ESPrimaEdgeType.WithContains.type]

  // labels
  implicit object labelContains extends ValidEdge[LabelNode, AnyNode, ESPrimaEdgeType.LabelContains.type]
  implicit object breakToLabel extends ValidEdge[BreakNode, LabelNode, ESPrimaEdgeType.BreakLabel.type]
  implicit object continueToLabel extends ValidEdge[ContinueNode, LabelNode, ESPrimaEdgeType.BreakLabel.type]

  // switch
  implicit object switchDiscriminant extends ValidEdge[SwitchNode, AnyNode, ESPrimaEdgeType.SwitchDiscriminant.type]
  implicit object switchBlock extends ValidEdge[SwitchNode, SwitchBlockNode, ESPrimaEdgeType.SwitchBlock.type]
  implicit object switchTest extends ValidEdge[SwitchBlockNode, AnyNode, ESPrimaEdgeType.SwitchTest.type]
  implicit object switchContains extends ValidEdge[SwitchBlockNode, AnyNode, ESPrimaEdgeType.SwitchContains.type]

  // If
  implicit object ifNode extends ValidEdge[IfNode, IfBlockNode, ESPrimaEdgeType.IfBlock.type]
  implicit object ifContains extends ValidEdge[IfBlockNode, AnyNode, ESPrimaEdgeType.IfContains.type]
  implicit object ifTest extends ValidEdge[IfBlockNode, AnyNode, ESPrimaEdgeType.IfTest.type]
  implicit object ifTestContains extends ValidEdge[IfBlockNode, AnyNode, ESPrimaEdgeType.IfTestContains.type]

  // Try
  implicit object tryContains extends ValidEdge[TryNode, AnyNode, ESPrimaEdgeType.TryContains.type]
  implicit object catchContains extends ValidEdge[CatchNode, AnyNode, ESPrimaEdgeType.CatchContains.type]
  implicit object finallyContains extends ValidEdge[FinallyNode, AnyNode, ESPrimaEdgeType.FinallyContains.type]

  implicit object catchNode extends ValidEdge[TryNode, CatchNode, ESPrimaEdgeType.Catch.type]
  implicit object finallyNode extends ValidEdge[TryNode, FinallyNode, ESPrimaEdgeType.Finally.type]

  implicit object catchArg extends ValidEdge[CatchNode, CatchArgNode, ESPrimaEdgeType.FunctionArgument.type]

  // Throw
  implicit object throws extends ValidEdge[ThrowNode, AnyNode, ESPrimaEdgeType.Throw.type]

  /**
   * Expressions
   */
  // ArrayMember
  implicit object arrayMember extends ValidEdge[ArrayNode, AnyNode, ESPrimaEdgeType.ArrayMember.type]

  // Object
  implicit object objectProp extends ValidEdge[ObjectNode, ObjectPropertyNode, ESPrimaEdgeType.ObjectProperty.type]
  implicit object objectKey extends ValidEdge[ObjectPropertyNode, AnyNode, ESPrimaEdgeType.ObjectKey.type]
  implicit object objectValue extends ValidEdge[ObjectPropertyNode, AnyNode, ESPrimaEdgeType.ObjectValue.type]
  implicit object objectSpread extends ValidEdge[ObjectNode, SpreadNode, ESPrimaEdgeType.ObjectSpread.type]

  // Member
  implicit object member extends ValidEdge[MemberNode, AnyNode, ESPrimaEdgeType.Member.type]
  implicit object memberKey extends ValidEdge[MemberNode, AnyNode, ESPrimaEdgeType.MemberKey.type]

  // conditional
  implicit object conditionalTest extends ValidEdge[ConditionalNode, AnyNode, ESPrimaEdgeType.ConditionalTest.type]
  implicit object conditionalConsequent extends ValidEdge[ConditionalNode, AnyNode, ESPrimaEdgeType.ConditionalConsequent.type]
  implicit object conditionalAlternate extends ValidEdge[ConditionalNode, AnyNode, ESPrimaEdgeType.ConditionalAlternate.type]

  // LogicalExpression
  implicit object binaryExpression extends ValidEdge[AnyNode, BinaryExpressionNode, ESPrimaEdgeType.BasicExpression.type]
  implicit object unaryExpression extends ValidEdge[AnyNode, UnaryExpressionNode, ESPrimaEdgeType.BasicExpression.type]

  // JSX
  implicit object jsxChild extends ValidEdge[JSXElementNode, AnyNode, ESPrimaEdgeType.JSXChild.type]
  implicit object jsxTag extends ValidEdge[JSXElementNode, AnyNode, ESPrimaEdgeType.JSXTag.type]
  implicit object jsxAttribute extends ValidEdge[JSXElementNode, JSXAttributeNode, ESPrimaEdgeType.JSXAttribute.type]
  implicit object jsxSpreadAttribute extends ValidEdge[JSXElementNode, SpreadNode, ESPrimaEdgeType.JSXAttribute.type]
  implicit object jsxAttributeValue extends ValidEdge[JSXAttributeNode, AnyNode, ESPrimaEdgeType.JSXAttributeValue.type]

  // Literal
  implicit object templateLiteral extends ValidEdge[TemplateLiteralNode, LiteralNode, ESPrimaEdgeType.TemplateLiteral.type]
  implicit object templateExpression extends ValidEdge[TemplateLiteralNode, TemplateExpressionNode, ESPrimaEdgeType.TemplateLiteral.type]
  implicit object templateContains extends ValidEdge[TemplateExpressionNode, AnyNode, ESPrimaEdgeType.TemplateContains.type]
}

sealed trait CanIndex[T <: ESPrimaEdgeType] extends CanIndexConstraint[T]

object CanIndex {
  implicit object ArrayMember extends CanIndex[ESPrimaEdgeType.ArrayMember.type]
  implicit object Argument extends CanIndex[ESPrimaEdgeType.Argument.type]
  implicit object FunctionArgument extends CanIndex[ESPrimaEdgeType.FunctionArgument.type]
  implicit object BasicExpression extends CanIndex[ESPrimaEdgeType.BasicExpression.type]

  implicit object IfBlock extends CanIndex[ESPrimaEdgeType.IfBlock.type]
  implicit object SwitchBlock extends CanIndex[ESPrimaEdgeType.SwitchBlock.type]

  implicit object Member extends CanIndex[ESPrimaEdgeType.Member.type]

  implicit object TemplateLiteral extends CanIndex[ESPrimaEdgeType.TemplateLiteral.type]

  implicit object JSXChild extends CanIndex[ESPrimaEdgeType.JSXChild.type]
}

sealed trait CanName[T <: ESPrimaEdgeType] extends CanNameConstraint[T]

object CanName {
  implicit object objectProperty extends CanName[ESPrimaEdgeType.ObjectProperty.type]
  implicit object member extends CanName[ESPrimaEdgeType.Member.type]
  implicit object exportKey extends CanName[ESPrimaEdgeType.ExportKey.type]
  implicit object jsxAttribute extends CanName[ESPrimaEdgeType.JSXAttribute.type]

  implicit object method extends CanName[ESPrimaEdgeType.Method.type]
  implicit object classProperty extends CanName[ESPrimaEdgeType.ClassProperty.type]
}
