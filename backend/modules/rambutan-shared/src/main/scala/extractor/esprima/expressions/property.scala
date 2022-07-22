package models.extractor.esprima

import models.CodeRange
import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Property {
  def computeName(node: ESPrimaNodeBuilder) = {
    node match {
      case LiteralNode(_, _, _, v)             => Some(v.asOpt[String].getOrElse(v.toString))
      case IdentifierReferenceNode(_, _, name) => Some(name)
      case IdentifierNode(_, _, name)          => Some(name)
      case _                                   => None
    }
  }

  def computeExpression(prop: ExpressionWrapper[ESPrimaNodeBuilder]) = {
    prop.node match {
      case LiteralNode(_, _, _, _)             => None
      case IdentifierReferenceNode(_, _, name) => None
      case IdentifierNode(_, _, name)          => None
      case _                                   => Some(prop)
    }
  }

  sealed case class PropertyWrapper(
    key:       ExpressionWrapper[ESPrimaNodeBuilder],
    value:     Option[ExpressionWrapper[ESPrimaNodeBuilder]],
    codeRange: CodeRange,
    kind:      String,
    computed:  Boolean,
    method:    Boolean,
    shorthand: Boolean)

  def Property = {
    node("Property") ~
      tup("key" -> Expressions.expression) ~
      tup("value" -> opt(Expressions.expression)) ~ // AssignmentPattern | BindingName
      tup("computed" -> Extractor.bool) ~
      tup("kind" -> Extractor.enum("get", "set", "init")) ~
      tup("method" -> Extractor.bool) ~
      tup("shorthand" -> Extractor.bool)
  } mapExtraction {
    case (context, codeRange, (((((key, value), computed), kind), method), shorthand)) => {
      PropertyWrapper(key, value, codeRange, kind, computed, method, shorthand)
    }
  }
}
