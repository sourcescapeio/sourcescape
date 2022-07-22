package models.index.esprima

import models.index.{ EdgeType, NodeType }
import silvousplay.imports._

/**
 * Nodes
 */
sealed abstract class ESPrimaNodeType(val identifier: String) extends NodeType

object ESPrimaNodeType extends Plenumeration[ESPrimaNodeType] {

  // is this even needed?
  case object Sequence extends ESPrimaNodeType("sequence")

  case object Require extends ESPrimaNodeType("require")
  case object Export extends ESPrimaNodeType("export")
  case object ExportKey extends ESPrimaNodeType("export-key")

  case object Identifier extends ESPrimaNodeType("identifier")
  case object IdentifierRef extends ESPrimaNodeType("identifier-ref")
  case object Member extends ESPrimaNodeType("member")

  case object Class extends ESPrimaNodeType("class")
  case object ClassProperty extends ESPrimaNodeType("class-property")
  case object Method extends ESPrimaNodeType("method")
  case object Instance extends ESPrimaNodeType("instance")
  case object Super extends ESPrimaNodeType("super")
  case object This extends ESPrimaNodeType("this")

  case object Call extends ESPrimaNodeType("call")

  case object Literal extends ESPrimaNodeType("literal")
  case object Array extends ESPrimaNodeType("array")

  case object BinaryExp extends ESPrimaNodeType("binary-exp")
  case object UnaryExp extends ESPrimaNodeType("unary-exp")

  // Control
  case object With extends ESPrimaNodeType("with")

  case object If extends ESPrimaNodeType("if")
  case object IfBlock extends ESPrimaNodeType("if-block")
  case object Conditional extends ESPrimaNodeType("conditional")

  case object Switch extends ESPrimaNodeType("switch")
  case object SwitchBlock extends ESPrimaNodeType("switch-block")
  case object Break extends ESPrimaNodeType("break")
  case object Continue extends ESPrimaNodeType("continue")

  case object Label extends ESPrimaNodeType("label")

  // Loops
  case object While extends ESPrimaNodeType("while")
  case object For extends ESPrimaNodeType("for")
  case object LoopVar extends ESPrimaNodeType("loop-var")

  // Error handling
  case object Try extends ESPrimaNodeType("try")
  case object Catch extends ESPrimaNodeType("catch")
  case object CatchArg extends ESPrimaNodeType("catch-arg")
  case object Finally extends ESPrimaNodeType("finally")

  case object Throw extends ESPrimaNodeType("throw")

  // Objects
  case object Object extends ESPrimaNodeType("object")
  case object ObjectProp extends ESPrimaNodeType("object-prop")

  // Functions
  case object Function extends ESPrimaNodeType("function")
  case object FunctionArg extends ESPrimaNodeType("function-arg")
  case object Spread extends ESPrimaNodeType("spread")
  case object Return extends ESPrimaNodeType("return")
  case object Await extends ESPrimaNodeType("await")
  case object Yield extends ESPrimaNodeType("yield")

  // JSX
  case object JSXElement extends ESPrimaNodeType("jsx-element")
  case object JSXAttribute extends ESPrimaNodeType("jsx-attribute")

  // Templates
  case object TemplateLiteral extends ESPrimaNodeType("template-literal")
  case object TaggedTemplate extends ESPrimaNodeType("tagged-template")
  case object TemplateExpression extends ESPrimaNodeType("template-expression")
}

sealed abstract class ESPrimaEdgeType(val identifier: String) extends EdgeType

trait ContainsEdgeType {
  self: ESPrimaEdgeType =>

  override val isContains = true
}

/**
 * Edges
 */
object ESPrimaEdgeType extends Plenumeration[ESPrimaEdgeType] {

  case object Declare extends ESPrimaEdgeType("declare")

  // contains types
  case object FunctionContains extends ESPrimaEdgeType("function-contains") with ContainsEdgeType
  case object WhileContains extends ESPrimaEdgeType("while-contains") with ContainsEdgeType
  case object ForContains extends ESPrimaEdgeType("for-contains") with ContainsEdgeType
  case object IfContains extends ESPrimaEdgeType("if-contains") with ContainsEdgeType
  case object IfTestContains extends ESPrimaEdgeType("if-test-contains") with ContainsEdgeType
  case object LabelContains extends ESPrimaEdgeType("label-contains") with ContainsEdgeType
  case object WithContains extends ESPrimaEdgeType("with-contains") with ContainsEdgeType
  case object SwitchContains extends ESPrimaEdgeType("switch-contains") with ContainsEdgeType
  case object TryContains extends ESPrimaEdgeType("try-contains") with ContainsEdgeType
  case object CatchContains extends ESPrimaEdgeType("catch-contains") with ContainsEdgeType
  case object FinallyContains extends ESPrimaEdgeType("finally-contains") with ContainsEdgeType
  case object TemplateContains extends ESPrimaEdgeType("template-contains")

  // Instantiation
  // from = instance [InstantiationNode]
  // to = class [Identifier or some shit]
  case object Class extends ESPrimaEdgeType("class")
  case object Constructor extends ESPrimaEdgeType("constructor")
  case object Method extends ESPrimaEdgeType("method")
  case object MethodKey extends ESPrimaEdgeType("method-key")
  case object MethodFunction extends ESPrimaEdgeType("method-function")
  case object ClassProperty extends ESPrimaEdgeType("class-property")
  case object ClassPropertyKey extends ESPrimaEdgeType("class-property-key")
  case object ClassPropertyValue extends ESPrimaEdgeType("class-property-value")
  case object SuperClass extends ESPrimaEdgeType("super-class")

  case object Assignment extends ESPrimaEdgeType("assignment")
  case object Reference extends ESPrimaEdgeType("reference")

  // loops
  case object WhileTest extends ESPrimaEdgeType("while-test")
  case object ForVar extends ESPrimaEdgeType("for-var")
  case object ForInit extends ESPrimaEdgeType("for-init")
  case object ForTest extends ESPrimaEdgeType("for-test")
  case object ForUpdate extends ESPrimaEdgeType("for-update")
  case object LoopDeclare extends ESPrimaEdgeType("loop-declare")

  // functions
  case object FunctionReturn extends ESPrimaEdgeType("function-return")
  case object Spread extends ESPrimaEdgeType("spread")
  case object FunctionArgument extends ESPrimaEdgeType("function-argument")
  case object Await extends ESPrimaEdgeType("await")
  // await contains todo
  case object Yield extends ESPrimaEdgeType("yield")
  // yield contains todo
  case object Return extends ESPrimaEdgeType("return")
  case object ReturnContains extends ESPrimaEdgeType("return-contains")

  // objects
  case object ObjectKey extends ESPrimaEdgeType("object-key")
  case object ObjectValue extends ESPrimaEdgeType("object-value")
  case object ObjectProperty extends ESPrimaEdgeType("object-prop")
  case object ObjectSpread extends ESPrimaEdgeType("object-spread")

  case object Call extends ESPrimaEdgeType("call")
  case object Export extends ESPrimaEdgeType("export")
  case object ExportKey extends ESPrimaEdgeType("export-key")

  case object Member extends ESPrimaEdgeType("member")
  case object MemberKey extends ESPrimaEdgeType("member-key")

  /**
   * Control
   */
  case object With extends ESPrimaEdgeType("with")

  // Switch
  case object SwitchDiscriminant extends ESPrimaEdgeType("switch-discriminant")
  case object SwitchBlock extends ESPrimaEdgeType("switch-block")
  case object SwitchTest extends ESPrimaEdgeType("switch-test")

  // If
  case object IfBlock extends ESPrimaEdgeType("if-block")
  case object IfTest extends ESPrimaEdgeType("if-test")
  // conditional
  case object ConditionalTest extends ESPrimaEdgeType("cond-test")
  case object ConditionalConsequent extends ESPrimaEdgeType("cond-consequent")
  case object ConditionalAlternate extends ESPrimaEdgeType("cond-alternate")

  // catch
  case object Catch extends ESPrimaEdgeType("catch")
  case object Finally extends ESPrimaEdgeType("finally")

  // throw
  case object Throw extends ESPrimaEdgeType("throw")

  // break
  case object BreakLabel extends ESPrimaEdgeType("break-label")

  // Indexed
  case object Argument extends ESPrimaEdgeType("argument")
  case object ArrayMember extends ESPrimaEdgeType("array-member")

  case object JSXChild extends ESPrimaEdgeType("jsx-child")
  case object JSXTag extends ESPrimaEdgeType("jsx-tag")
  case object JSXAttribute extends ESPrimaEdgeType("jsx-attribute")
  case object JSXAttributeValue extends ESPrimaEdgeType("jsx-attribute-value")

  case object BasicExpression extends ESPrimaEdgeType("basic-exp")
  case object IfExpression extends ESPrimaEdgeType("if-exp")

  case object TemplateLiteral extends ESPrimaEdgeType("template-literal")
}
