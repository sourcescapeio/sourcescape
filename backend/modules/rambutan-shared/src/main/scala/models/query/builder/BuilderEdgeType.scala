package models.query

import silvousplay.imports._
import com.typesafe.config.ConfigException.Generic

sealed abstract class BuilderEdgePriority(val identifier: String, val priority: Int, val reference: Boolean) extends Identifiable

object BuilderEdgePriority extends Plenumeration[BuilderEdgePriority] {

  case object Required extends BuilderEdgePriority("required", 1, false)
  case object Reference extends BuilderEdgePriority("reference", 2, true)
  case object ContainsHigh extends BuilderEdgePriority("contains-high", 3, false)
  case object ContainsLow extends BuilderEdgePriority("contains-low", 4, false)

}

sealed abstract class BuilderEdgeType(val identifier: String, val priority: BuilderEdgePriority, val isTraverse: Boolean = false) extends Identifiable {
  def isContains = {
    priority match {
      case BuilderEdgePriority.ContainsHigh => true
      case BuilderEdgePriority.ContainsLow  => true
      case _                                => false
    }
  }
}

object ScalaBuilderEdgeType extends Plenumeration[BuilderEdgeType] {

}

object RubyBuilderEdgeType extends Plenumeration[BuilderEdgeType] {
  case object Const extends BuilderEdgeType("const", BuilderEdgePriority.Required, isTraverse = true)
  case object Send extends BuilderEdgeType("send", BuilderEdgePriority.Required, isTraverse = true)

  case object SendArg extends BuilderEdgeType("send-arg", BuilderEdgePriority.Reference)

  case object ArrayElement extends BuilderEdgeType("array_element", BuilderEdgePriority.Reference)
  case object HashElement extends BuilderEdgeType("hash_element", BuilderEdgePriority.Required)
  case object PairValue extends BuilderEdgeType("pair_value", BuilderEdgePriority.Reference)
}

object JavascriptBuilderEdgeType extends Plenumeration[BuilderEdgeType] {
  case object JSXAttribute extends BuilderEdgeType("jsx_attribute", BuilderEdgePriority.Required)
  case object JSXAttributeValue extends BuilderEdgeType("jsx_attribute_value", BuilderEdgePriority.Reference)
  case object JSXChild extends BuilderEdgeType("jsx_child", BuilderEdgePriority.Reference)
  case object JSXTag extends BuilderEdgeType("jsx_tag", BuilderEdgePriority.Reference)

  case object BinaryLeft extends BuilderEdgeType("binary_left", BuilderEdgePriority.Reference)
  case object BinaryRight extends BuilderEdgeType("binary_right", BuilderEdgePriority.Reference)
  case object UnaryExpression extends BuilderEdgeType("unary_expression", BuilderEdgePriority.Reference)

  case object ObjectProperty extends BuilderEdgeType("object_property", BuilderEdgePriority.Required)
  case object ObjectPropertyValue extends BuilderEdgeType("object_property_value", BuilderEdgePriority.Reference)
  case object ArrayMember extends BuilderEdgeType("array_member", BuilderEdgePriority.Reference)

  case object ClassExtends extends BuilderEdgeType("class_extends", BuilderEdgePriority.Reference)
  case object ClassMethod extends BuilderEdgeType("class_method", BuilderEdgePriority.Required)
  case object ClassProperty extends BuilderEdgeType("class_property", BuilderEdgePriority.Required)
  case object ClassPropertyValue extends BuilderEdgeType("class_property_value", BuilderEdgePriority.Reference)

  case object InstanceOf extends BuilderEdgeType("instance_of", BuilderEdgePriority.Reference)
  case object InstanceArg extends BuilderEdgeType("instance_arg", BuilderEdgePriority.Reference)

  case object MethodArg extends BuilderEdgeType("method_arg", BuilderEdgePriority.Required)
  case object MethodContains extends BuilderEdgeType("method_contains", BuilderEdgePriority.ContainsHigh)

  case object FunctionArg extends BuilderEdgeType("function_arg", BuilderEdgePriority.Required)
  case object FunctionContains extends BuilderEdgeType("function_contains", BuilderEdgePriority.ContainsLow)
  case object Return extends BuilderEdgeType("return", BuilderEdgePriority.Reference)
  case object Yield extends BuilderEdgeType("yield", BuilderEdgePriority.Reference)
  case object Await extends BuilderEdgeType("await", BuilderEdgePriority.Reference)
  case object Throw extends BuilderEdgeType("throw", BuilderEdgePriority.Reference)

  case object Member extends BuilderEdgeType("member", BuilderEdgePriority.Required, isTraverse = true)
  case object Call extends BuilderEdgeType("call", BuilderEdgePriority.Required, isTraverse = true)
  case object CallArg extends BuilderEdgeType("call_arg", BuilderEdgePriority.Reference)

  case object IfCondition extends BuilderEdgeType("if_condition", BuilderEdgePriority.Reference)
  case object IfBody extends BuilderEdgeType("if_body", BuilderEdgePriority.Reference)

  case object TemplateComponent extends BuilderEdgeType("template_component", BuilderEdgePriority.Required)
  case object TemplateContains extends BuilderEdgeType("template_contains", BuilderEdgePriority.ContainsLow)
}

object BuilderEdgeType {

  case object RequireDependency extends BuilderEdgeType("require_dependency", BuilderEdgePriority.Reference)

  private def fromJavascriptPredicate(predicate: JavascriptEdgePredicate) = {
    predicate match {
      case JavascriptEdgePredicate.JSXAttribute        => JavascriptBuilderEdgeType.JSXAttribute
      case JavascriptEdgePredicate.JSXAttributeValue   => JavascriptBuilderEdgeType.JSXAttributeValue
      case JavascriptEdgePredicate.JSXChild            => JavascriptBuilderEdgeType.JSXChild
      case JavascriptEdgePredicate.JSXTag              => JavascriptBuilderEdgeType.JSXTag
      //
      case JavascriptEdgePredicate.BinaryLeft          => JavascriptBuilderEdgeType.BinaryLeft
      case JavascriptEdgePredicate.BinaryRight         => JavascriptBuilderEdgeType.BinaryRight
      case JavascriptEdgePredicate.UnaryExpression     => JavascriptBuilderEdgeType.UnaryExpression
      //
      case JavascriptEdgePredicate.ArrayMember         => JavascriptBuilderEdgeType.ArrayMember
      case JavascriptEdgePredicate.ObjectProperty      => JavascriptBuilderEdgeType.ObjectProperty
      case JavascriptEdgePredicate.ObjectPropertyValue => JavascriptBuilderEdgeType.ObjectPropertyValue
      //
      case JavascriptEdgePredicate.ClassExtends        => JavascriptBuilderEdgeType.ClassExtends
      case JavascriptEdgePredicate.ClassMethod         => JavascriptBuilderEdgeType.ClassMethod
      case JavascriptEdgePredicate.ClassProperty       => JavascriptBuilderEdgeType.ClassProperty
      case JavascriptEdgePredicate.ClassPropertyValue  => JavascriptBuilderEdgeType.ClassPropertyValue
      case JavascriptEdgePredicate.InstanceOf          => JavascriptBuilderEdgeType.InstanceOf
      case JavascriptEdgePredicate.InstanceArg         => JavascriptBuilderEdgeType.InstanceArg
      case JavascriptEdgePredicate.MethodArg           => JavascriptBuilderEdgeType.MethodArg
      case JavascriptEdgePredicate.MethodContains      => JavascriptBuilderEdgeType.MethodContains
      //
      case JavascriptEdgePredicate.FunctionArg         => JavascriptBuilderEdgeType.FunctionArg
      case JavascriptEdgePredicate.FunctionContains    => JavascriptBuilderEdgeType.FunctionContains
      case JavascriptEdgePredicate.Return              => JavascriptBuilderEdgeType.Return
      case JavascriptEdgePredicate.Yield               => JavascriptBuilderEdgeType.Yield
      case JavascriptEdgePredicate.Await               => JavascriptBuilderEdgeType.Await
      case JavascriptEdgePredicate.Throw               => JavascriptBuilderEdgeType.Throw
      //
      case JavascriptEdgePredicate.Member              => JavascriptBuilderEdgeType.Member
      case JavascriptEdgePredicate.Call                => JavascriptBuilderEdgeType.Call
      case JavascriptEdgePredicate.CallArg             => JavascriptBuilderEdgeType.CallArg
      case JavascriptEdgePredicate.IfCondition         => JavascriptBuilderEdgeType.IfCondition
      case JavascriptEdgePredicate.IfBody              => JavascriptBuilderEdgeType.IfBody
      //
      case JavascriptEdgePredicate.TemplateComponent   => JavascriptBuilderEdgeType.TemplateComponent
      case JavascriptEdgePredicate.TemplateContains    => JavascriptBuilderEdgeType.TemplateContains
    }
  }

  private def fromScalaPredicate(predicate: ScalaEdgePredicate): BuilderEdgeType = {
    predicate match {
      case _ => throw new Exception("not implemented")
    }
  }

  private def fromRubyPredicate(predicate: RubyEdgePredicate): BuilderEdgeType = {
    predicate match {
      case RubyEdgePredicate.Const        => RubyBuilderEdgeType.Const
      case RubyEdgePredicate.Send         => RubyBuilderEdgeType.Send
      case RubyEdgePredicate.SendArg      => RubyBuilderEdgeType.SendArg
      case RubyEdgePredicate.ArrayElement => RubyBuilderEdgeType.ArrayElement
      case RubyEdgePredicate.HashElement  => RubyBuilderEdgeType.HashElement
      case RubyEdgePredicate.PairValue    => RubyBuilderEdgeType.PairValue
    }
  }

  def fromPredicate(predicate: EdgePredicate) = {
    predicate match {
      case j: JavascriptEdgePredicate               => fromJavascriptPredicate(j)
      case s: ScalaEdgePredicate                    => fromScalaPredicate(s)
      case r: RubyEdgePredicate                     => fromRubyPredicate(r)
      // specials
      case UniversalEdgePredicate.RequireDependency => BuilderEdgeType.RequireDependency
      case g: GenericEdgePredicate                  => throw new Exception("not implemented")
    }
  }

  // Should never be accessed?
  // lazy val all = List(RequireDependency) ++ JavascriptBuilderEdgeType.all ++ ScalaBuilderEdgeType.all ++ RubyBuilderEdgeType.all
}
