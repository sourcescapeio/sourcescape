package models.extractor.esprima

import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Variables {

  def MemberExpression = {
    node("MemberExpression") ~
      tup("computed" -> Extractor.bool) ~
      tup("object" -> Expressions.expression) ~
      tup("property" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, ((computed, obj), prop)) => {
      val maybeName = Property.computeName(prop.node)

      // Use code range of property
      val memberNode = MemberNode(Hashing.uuid(), prop.codeRange, maybeName)

      val maybeExp = Property.computeExpression(prop)

      val memberEdge = CreateEdge(
        memberNode,
        AnyNode(obj.node),
        ESPrimaEdgeType.Member).named(maybeName.getOrElse(""))

      val memberKeyEdge = withDefined(maybeExp) { exp =>
        Option apply CreateEdge(
          memberNode,
          AnyNode(exp.node),
          ESPrimaEdgeType.MemberKey).edge
      }

      ExpressionWrapper(
        memberNode,
        codeRange,
        obj :: maybeExp.toList,
        Nil,
        memberEdge :: memberKeyEdge.toList)
    }
  }

  private def detectExports(item: ExpressionWrapper[ESPrimaNodeBuilder]) = {
    (item.children.headOption.map(_.node), item.node) match {
      case (Some(IdentifierReferenceNode(_, _, "module")), MemberNode(id, _, Some("exports"))) => true
      case (_, IdentifierReferenceNode(_, _, "exports")) => true
      case _ => false
    }
  }

  private val moduleExports = Expressions.expression.mapValue(detectExports)
  private val moduleExportsNamed = Expressions.expression.mapValue { left =>
    (left.children.headOption, left.node) match {
      case (Some(leftChild), MemberNode(_, _, Some(_))) => detectExports(leftChild)
      case _ => false
    }
  }

  private def ModuleObject = {
    node("ObjectExpression") ~
      tup("properties" -> list(Property.Property))
  } mapExtraction {
    case (context, codeRange, properties) => {
      properties.map { prop =>
        val k = prop.key.node match {
          case IdentifierReferenceNode(_, _, name) => name
          case _                                   => throw new Exception("invalid")
        }
        val v = prop.value match {
          case Some(v) => v
          case None    => prop.key
        }

        (k, v, prop.codeRange)
      }
    }
  }

  // module.exports = ...
  private def ModuleDirectAssignment = {
    node("AssignmentExpression") ~~
      constraint("operator" -> Constraints.str("=")) ~~
      constraint("left" -> moduleExports) ~
      tup("right" -> (ModuleObject or Expressions.expression))
  } mapBoth {
    case (context, codeRange, Left(moduleObject)) => {
      val exportNode = context.getExport(codeRange)

      val keyExpressions = moduleObject map {
        case (k, v, propRange) => {
          val exportKeyNode = ExportKeyNode(Hashing.uuid, propRange, k)
          val exportedEdge = CreateEdge(AnyNode(v.node), exportKeyNode, ESPrimaEdgeType.Export).edge
          val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(k)

          ExpressionWrapper(
            exportKeyNode,
            propRange,
            v :: Nil,
            Nil,
            exportedEdge :: exportKeyEdge :: Nil)
        }
      }

      // This will replace context
      (
        context.replaceExport(exportNode),
        ExpressionWrapper(
          exportNode,
          codeRange,
          keyExpressions,
          Nil,
          Nil))
    }
    case (context, codeRange, Right(right)) => {
      val exportNode = context.getExport(codeRange)
      val edge = CreateEdge(AnyNode(right.node), exportNode, ESPrimaEdgeType.Export).edge

      // This will replace context
      (
        context.replaceExport(exportNode),
        ExpressionWrapper(
          exportNode,
          codeRange,
          right :: Nil,
          Nil,
          edge :: Nil))
    }
  }

  // exports.[name] =
  private def ModuleNamedAssignment = {
    node("AssignmentExpression") ~~
      constraint("operator" -> Constraints.str("=")) ~~
      constraint("left" -> moduleExportsNamed) ~
      tup("left" -> MemberExpression) ~
      tup("right" -> Expressions.expression)
  } mapBoth {
    case (context, codeRange, (left, right)) => {
      val exportNode = context.getExport(codeRange)

      // NOTE, we don't look for dupes in export key
      val memberName = left.node.name.getOrElse("")
      val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, memberName)

      val exportedEdge = CreateEdge(AnyNode(right.node), exportKeyNode, ESPrimaEdgeType.Export).edge
      val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(memberName)

      // This will add to context
      (
        context.pushExport(exportNode),
        ExpressionWrapper(
          exportKeyNode,
          codeRange,
          right :: Nil,
          withFlag(context.currentExport.isEmpty) {
            exportNode :: Nil
          },
          exportedEdge :: exportKeyEdge :: Nil))
    }
  }

  private def AssignmentExpressionBase = {
    node("AssignmentExpression") ~
      tup("operator" -> Extractor.str) ~
      tup("left" -> Expressions.expression) ~
      tup("right" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, ((operator, left), right)) => {
      // dealing with module
      val node = BinaryExpressionNode(Hashing.uuid, codeRange, operator)

      val argEdges = List(
        CreateEdge(AnyNode(left.node), node, ESPrimaEdgeType.BasicExpression).indexed(0),
        CreateEdge(AnyNode(right.node), node, ESPrimaEdgeType.BasicExpression).indexed(1))

      ExpressionWrapper(
        node,
        codeRange,
        left :: right :: Nil,
        Nil,
        argEdges)
    }
  }

  def AssignmentExpression = ModuleDirectAssignment | ModuleNamedAssignment | AssignmentExpressionBase

  // ModuleAssignments need to happen before Assignment
  def expressions = MemberExpression | AssignmentExpression
}
