package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._
import play.api.libs.json._

object Collections {
  private def TemplateElement = {
    node("TemplateElement") ~
      tup("value" -> Extractor.js) ~
      tup("tail" -> Extractor.bool)
  } mapExtraction {
    case (context, codeRange, (value, tail)) => {
      (codeRange, (value \ "raw").as[String], tail)
    }
  }

  def TemplateLiteral = {
    node("TemplateLiteral") ~
      tup("quasis" -> list(TemplateElement)) ~
      tup("expressions" -> list(Expressions.expression))
  } mapExtraction {
    case (context, codeRange, (quasis, expressions)) => {
      val mappedQuasis = quasis.flatMap {
        case (range, v, tail) if v.length > 0 => {
          // startAdjust is always 1
          // either ` or } starts

          val endAdjust = if (tail) {
            // `
            1
          } else {
            // ${
            2
          }

          val correctedRange = range.copy(
            start = range.start.copy(
              column = range.start.column + 1),
            end = range.end.copy(
              column = range.end.column - endAdjust),
            startIndex = range.startIndex + 1,
            endIndex = range.endIndex - endAdjust)
          Some((LiteralNode(Hashing.uuid, correctedRange, v, JsString(v)), Nil))
        }
        case _ => None
      }

      val mappedExpressions = expressions.map { ex =>
        val expNode = TemplateExpressionNode(Hashing.uuid, ex.codeRange)
        (
          expNode,
          ex.allNodes.map { n =>
            CreateEdge(expNode, AnyNode(n), ESPrimaEdgeType.TemplateContains).edge
          })
      }

      val literalNode = TemplateLiteralNode(Hashing.uuid, codeRange)

      val templateEdges = (mappedQuasis ++ mappedExpressions)
        .map(_._1)
        .sortBy(_.range.startIndex)
        .zipWithIndex
        .map {
          case (n @ LiteralNode(_, _, _, _), idx) => {
            CreateEdge(literalNode, n, ESPrimaEdgeType.TemplateLiteral).indexed(idx)
          }
          case (n @ TemplateExpressionNode(_, _), idx) => {
            CreateEdge(literalNode, n, ESPrimaEdgeType.TemplateLiteral).indexed(idx)
          }
          case _ => throw new Exception("fail")
        }

      ExpressionWrapper(
        literalNode,
        codeRange,
        expressions,
        mappedQuasis.map(_._1) ++ mappedExpressions.map(_._1),
        mappedQuasis.flatMap(_._2) ++ mappedExpressions.flatMap(_._2) ++ templateEdges)
    }
  }

  def ArrayExpressionElement = {
    Expressions.expression | Patterns.SpreadElement
  }

  def ArrayExpression = {
    node("ArrayExpression") ~
      tup("elements" -> list(ArrayExpressionElement))
  } mapExtraction {
    case (context, codeRange, elements) => {
      val arrayNode = ArrayNode(Hashing.uuid, codeRange)
      val edges = elements.zipWithIndex.map {
        case (e, idx) => CreateEdge(arrayNode, AnyNode(e.node), ESPrimaEdgeType.ArrayMember).indexed(idx)
      }

      ExpressionWrapper(
        arrayNode,
        codeRange,
        elements,
        Nil,
        edges)
    }
  }

  // Classes.MethodDefinition
  // TSAbstractMethodDefinition
  def ObjectExpression = {
    node("ObjectExpression") ~
      tup("properties" -> list(Property.Property or Patterns.SpreadElement))
  } mapExtraction {
    case (context, codeRange, properties) => {
      val obj = ObjectNode(Hashing.uuid, codeRange)

      val propertyExpressions = properties.flatMap {
        case Left(p)       => p.key :: p.value.toList
        case Right(spread) => spread :: Nil
      }

      val propertyItems = properties.flatMap {
        case Left(prop) => {
          val maybeName = Property.computeName(prop.key.node)
          val propNode = ObjectPropertyNode(Hashing.uuid, prop.codeRange, maybeName.getOrElse(""))
          val propEdge = CreateEdge(obj, propNode, ESPrimaEdgeType.ObjectProperty).named(maybeName.getOrElse(""))

          val propKey = CreateEdge(propNode, AnyNode(prop.key.node), ESPrimaEdgeType.ObjectKey).edge
          val propValue = withDefined(prop.value) { value =>
            List(CreateEdge(propNode, AnyNode(value.node), ESPrimaEdgeType.ObjectValue).edge)
          }

          Some((propNode, List(propEdge, propKey) ++ propValue))
        }
        case _ => None
      }

      val spreadEdges = properties.flatMap {
        case Right(spread) => {
          Some(CreateEdge(obj, spread.node, ESPrimaEdgeType.ObjectSpread).edge)
        }
        case _ => None
      }

      ExpressionWrapper(
        obj,
        codeRange,
        propertyExpressions,
        propertyItems.map(_._1),
        propertyItems.flatMap(_._2) ++ spreadEdges)
    }
  }

  def expressions = {
    ArrayExpression | ObjectExpression | TemplateLiteral
  }
}
