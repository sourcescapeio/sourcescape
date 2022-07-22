package models.extractor.esprima

import play.api.libs.json._
import silvousplay.imports._
import models.extractor._
import models.index.esprima._

//https://github.com/typescript-eslint/typescript-eslint/blob/e3836910/packages/typescript-estree/src/ts-estree/ts-estree.ts
object JSX {

  /**
   * Base
   */
  private val JSXIdentifier = {
    node("JSXIdentifier") ~
      tup("name" -> Extractor.str)
  }

  private def JSXMemberExpression: Extractor[ESPrimaContext, ExpressionWrapper[MemberNode]] = {
    node("JSXMemberExpression") ~
      tup("object" -> JSXTagNameExpression) ~
      tup("property" -> JSXIdentifier)
  } mapExtraction {
    case (context, codeRange, (obj, prop)) => {
      val memberNode = MemberNode(Hashing.uuid, codeRange, Some(prop))

      val memberEdge = CreateEdge(memberNode, AnyNode(obj.node), ESPrimaEdgeType.Member).named(prop)

      ExpressionWrapper(
        memberNode,
        codeRange,
        obj :: Nil,
        Nil,
        memberEdge :: Nil)
    }
  }

  private val MappedJSXIdentifier = JSXIdentifier mapExtraction {
    case (context, codeRange, name) => {
      Terminal.extractIdentifier(context, codeRange, name)
    }
  }

  private def JSXTagNameExpression = MappedJSXIdentifier | JSXMemberExpression

  /**
   * Expression
   */
  private val JSXEmptyExpression = {
    node("JSXEmptyExpression")
  } mapExtraction {
    case (context, codeRange, _) => None
  }

  private def JSXSpreadChild = {
    node("JSXSpreadChild") ~
      // This is | EmptyExpression in actual impl, but we're simplifying
      tup("expression" -> opt(Expressions.expression))
  } mapExtraction {
    case (context, codeRange, Some(expression)) => {
      Some(Patterns.applySpread(codeRange, expression))
    }
    case _ => None
  }

  private def JSXExpressionContainer = {
    node("JSXExpressionContainer") ~
      // This is | EmptyExpression in actual impl, but we're simplifying
      tup("expression" -> (JSXEmptyExpression or Expressions.expression))
  } mapExtraction {
    case (context, codeRange, Right(expression)) => {
      Some(expression)
    }
    case _ => None
  }

  type JSXExpressionType = ChainedExtractor[ESPrimaContext, Option[ExpressionWrapper[ESPrimaNodeBuilder]]]
  private def JSXExpression: JSXExpressionType = JSXEmptyExpression | JSXSpreadChild | JSXExpressionContainer

  /**
   * Structural
   */

  private def JSXSpreadAttribute = {
    node("JSXSpreadAttribute") ~
      tup("argument" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, argument) => {
      Patterns.applySpread(codeRange, argument)
    }
  }

  private def JSXAttribute = {
    node("JSXAttribute") ~
      tup("name" -> JSXIdentifier) ~
      tup("value" -> opt(Terminal.Literal or JSXExpression))
  } mapExtraction {
    case (context, codeRange, (name, value)) => {
      val maybeValue = value match {
        case Some(Left(lit))        => Some(lit)
        case Some(Right(Some(exp))) => Some(exp)
        case _                      => None
      }

      val attributeNode = JSXAttributeNode(Hashing.uuid, codeRange, name)
      val maybeEdge = maybeValue.map { v =>
        CreateEdge(attributeNode, AnyNode(v.node), ESPrimaEdgeType.JSXAttributeValue).edge
      }

      ExpressionWrapper(
        attributeNode,
        codeRange,
        maybeValue.toList,
        Nil,
        maybeEdge.toList)
    }
  }

  private def JSXOpeningElement = {
    node("JSXOpeningElement") ~
      tup("name" -> JSXTagNameExpression) ~
      tup("attributes" -> list(JSXAttribute or JSXSpreadAttribute))
  }

  /**
   * Other children
   */
  private def JSXFragment: Extractor[ESPrimaContext, ExpressionWrapper[JSXElementNode]] = {
    node("JSXFragment") ~
      // tup("openingFragment" -> JSXOpeningFragment) ~  << don't care
      // tup("closingFragment" -> JSXClosingFragment) ~ << don't care
      tup("children" -> list(JSXChild))
  } mapExtraction {
    case (context, codeRange, children) => {
      val jsxNode = JSXElementNode(Hashing.uuid, codeRange)

      val flattenedChildren = children.flatten

      val edges = flattenedChildren.zipWithIndex.map {
        case (child, idx) => CreateEdge(jsxNode, AnyNode(child.node), ESPrimaEdgeType.JSXChild).indexed(idx)
      }

      ExpressionWrapper(
        jsxNode,
        codeRange,
        flattenedChildren,
        Nil,
        edges)
    }
  }

  private val JSXText = {
    node("JSXText") ~
      tup("value" -> Extractor.str) ~
      tup("raw" -> Extractor.str)
  } mapExtraction {
    case (context, codeRange, (value, raw)) => {
      ExpressionWrapper.single(LiteralNode(Hashing.uuid, codeRange, raw, JsString(value)))
    }
  }

  type JSXChildType = ChainedExtractor[ESPrimaContext, Option[ExpressionWrapper[ESPrimaNodeBuilder]]]
  // private def JSXChild: JSXChildType = JSXElement |
  private def OptionJSXElement = JSXElement.mapExtraction {
    case (_, _, v) => Option(v)
  }
  private def OptionJSXFragment = JSXFragment.mapExtraction {
    case (_, _, v) => Option(v)
  }
  private def OptionJSXText = JSXText.mapExtraction {
    case (_, _, v) => Option(v)
  }
  private def OptionLiteral = Terminal.Literal.mapExtraction {
    case (_, _, v) => Option(v)
  }
  private def JSXChild: JSXChildType = {
    JSXExpression |
      OptionJSXElement |
      OptionJSXFragment |
      OptionJSXText |
      OptionLiteral
  }

  /**
   * Element
   */
  // element from context
  // val attributeToElement

  def JSXElement = {
    node("JSXElement") ~
      tup("openingElement" -> JSXOpeningElement) ~
      // tup("closingElement" -> ) ~ // don't care
      tup("children" -> list(JSXChild))
  } mapExtraction {
    case (context, codeRange, ((name, attributes), children)) => {
      val jsxNode = JSXElementNode(Hashing.uuid, codeRange)
      val jsxTagEdge = CreateEdge(jsxNode, AnyNode(name.node), ESPrimaEdgeType.JSXTag).edge

      val attributeEdges = attributes.map {
        case Left(attr)  => CreateEdge(jsxNode, attr.node, ESPrimaEdgeType.JSXAttribute).named(attr.node.name)
        case Right(attr) => CreateEdge(jsxNode, attr.node, ESPrimaEdgeType.JSXAttribute).edge
      }

      val attributeExpressions = attributes.map {
        case Left(attr)  => attr
        case Right(attr) => attr
      }

      val flattenedChildren = children.flatten

      val edges = flattenedChildren.zipWithIndex.map {
        case (child, idx) => CreateEdge(jsxNode, AnyNode(child.node), ESPrimaEdgeType.JSXChild).indexed(idx)
      }

      ExpressionWrapper(
        jsxNode,
        codeRange,
        name :: (attributeExpressions ++ flattenedChildren),
        Nil,
        (jsxTagEdge :: attributeEdges) ++ edges)
    }
  }

  def expressions = JSXElement | JSXFragment
}
