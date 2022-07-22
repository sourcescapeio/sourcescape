package models.index.scalameta

import models.index.{ ValidEdgeConstraint, CanIndexConstraint, CanNameConstraint }
import silvousplay.imports._

/**
 * Graph restrictions
 */
sealed abstract class ValidEdge[From <: ScalaMetaNode, To <: ScalaMetaNode, Z <: ScalaMetaEdgeType] extends ValidEdgeConstraint[ScalaMetaNodeType, From, To, Z]

object ValidEdge {
  //implicit object templateContains extends ValidEdge[TemplateExpressionNode, AnyNode, ESPrimaEdgeType.TemplateContains.type]
}

sealed trait CanIndex[T <: ScalaMetaEdgeType] extends CanIndexConstraint[T]

object CanIndex {
  //   implicit object ArrayMember extends CanIndex[ESPrimaEdgeType.ArrayMember.type]
  //   implicit object Argument extends CanIndex[ESPrimaEdgeType.Argument.type]
  //   implicit object FunctionArgument extends CanIndex[ESPrimaEdgeType.FunctionArgument.type]
  //   implicit object BasicExpression extends CanIndex[ESPrimaEdgeType.BasicExpression.type]

  //   implicit object IfBlock extends CanIndex[ESPrimaEdgeType.IfBlock.type]
  //   implicit object SwitchBlock extends CanIndex[ESPrimaEdgeType.SwitchBlock.type]

  //   implicit object Member extends CanIndex[ESPrimaEdgeType.Member.type]

  //   implicit object TemplateLiteral extends CanIndex[ESPrimaEdgeType.TemplateLiteral.type]

  //   implicit object JSXChild extends CanIndex[ESPrimaEdgeType.JSXChild.type]
}

sealed trait CanName[T <: ScalaMetaEdgeType] extends CanNameConstraint[T]

object CanName {
  //   implicit object objectProperty extends CanName[ESPrimaEdgeType.ObjectProperty.type]
  //   implicit object member extends CanName[ESPrimaEdgeType.Member.type]
  //   implicit object exportKey extends CanName[ESPrimaEdgeType.ExportKey.type]
  //   implicit object jsxAttribute extends CanName[ESPrimaEdgeType.JSXAttribute.type]

  //   implicit object method extends CanName[ESPrimaEdgeType.Method.type]
  //   implicit object classProperty extends CanName[ESPrimaEdgeType.ClassProperty.type]
}
