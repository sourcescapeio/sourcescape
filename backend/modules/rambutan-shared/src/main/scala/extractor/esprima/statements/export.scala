package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Export {

  private def ExportNamedDeclarationAsDecl = {
    node("ExportNamedDeclaration") ~~
      constraint("declaration" -> opt(node("ClassDeclaration") | node("FunctionDeclaration")).mapValue(_.isDefined)) ~
      tup("declaration" -> (Declaration.ClassDeclaration | Declaration.FunctionDeclaration))
  } mapBoth {
    case (context, codeRange, declaration) => {
      withDefined(declaration.expressions.headOption) { expression =>
        val maybeName = expression.additionalNodes.headOption.flatMap {
          case IdentifierNode(_, _, name) => Some(name)
          case _                          => None
        }

        withDefined(maybeName) { name =>
          val exportNode = context.getExport(codeRange)

          val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)

          val exportedEdge = CreateEdge(AnyNode(expression.node), exportKeyNode, ESPrimaEdgeType.Export).edge
          val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

          val wrapper = ExpressionWrapper(
            exportKeyNode,
            codeRange,
            Nil,
            withFlag(context.currentExport.isEmpty) {
              exportNode :: Nil
            },
            exportedEdge :: exportKeyEdge :: Nil)

          Option apply (
            context.pushExport(exportNode),
            StatementWrapper(
              codeRange,
              wrapper :: Nil,
              declaration :: Nil))
        }
      } getOrElse {
        (context, declaration)
      }
    }
  }

  private def ExportNamedDeclarationAsVar = {
    node("ExportNamedDeclaration") ~~
      constraint("declaration" -> opt(node("VariableDeclaration")).mapValue(_.isDefined)) ~
      tup("declaration" -> (Declaration.VariableDeclaration))
  } mapBoth {
    case (context, codeRange, declaration) => {
      // just pluck all identifiers
      // Note this only works with first level
      // i.e. export const a = ...
      val exportNode = context.getExport(codeRange)

      val rawIdentifiers = declaration.children.flatMap(_.expressions).flatMap(_.allIdentifiers)

      val all = rawIdentifiers.map {
        case i @ IdentifierNode(id, idRange, name) => {
          val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)
          val exportedEdge = CreateEdge(AnyNode(i), exportKeyNode, ESPrimaEdgeType.Export).edge
          val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

          ExpressionWrapper(
            exportKeyNode,
            idRange,
            Nil,
            Nil,
            exportedEdge :: exportKeyEdge :: Nil)
        }
      }

      (
        context.pushExport(exportNode),
        StatementWrapper(
          codeRange,
          all ++ withFlag(context.currentExport.isEmpty) {
            ExpressionWrapper(exportNode, codeRange, Nil, Nil, Nil) :: Nil
          },
          declaration :: Nil))
    }
  }

  private val ExportSpecifier = {
    node("ExportSpecifier") ~
      tup("exported" -> Terminal.Identifier)
  }

  // there should only
  private def ExportNamedDeclarationAsSpec = {
    node("ExportNamedDeclaration") ~
      tup("source" -> opt(Terminal.Literal)) ~
      tup("specifiers" -> list(ExportSpecifier))
  } mapBoth {
    case (context, codeRange, (None, specifiers)) => {
      val exportNode = context.getExport(codeRange)

      val all = specifiers.map { id =>
        val name = id.node.name
        val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)

        val exportedEdge = CreateEdge(AnyNode(id.node), exportKeyNode, ESPrimaEdgeType.Export).edge
        val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

        ExpressionWrapper(
          exportKeyNode,
          id.node.range,
          id :: Nil,
          Nil,
          exportedEdge :: exportKeyEdge :: Nil)
      }

      (
        context.pushExport(exportNode),
        StatementWrapper(
          codeRange,
          all ++ withFlag(context.currentExport.isEmpty) {
            ExpressionWrapper(exportNode, codeRange, Nil, Nil, Nil) :: Nil
          },
          Nil))
    }
    case (context, codeRange, (Some(source), specifiers)) => {
      val exportNode = context.getExport(codeRange)

      val requireNode = context.requireNode(source.node.range, source.node.value.as[String])

      val all = specifiers.map { id =>
        val name = id.node.name

        val memberNode = MemberNode(Hashing.uuid, codeRange, Some(name))
        val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)

        val exportedEdge = CreateEdge(AnyNode(memberNode), exportKeyNode, ESPrimaEdgeType.Export).edge
        val memberEdge = CreateEdge(memberNode, AnyNode(requireNode), ESPrimaEdgeType.Member).named(name)
        val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

        ExpressionWrapper(
          exportKeyNode,
          id.node.range,
          Nil,
          memberNode :: Nil,
          exportedEdge :: memberEdge :: exportKeyEdge :: Nil)
      }

      val rootExpression = ExpressionWrapper(
        requireNode,
        codeRange,
        Nil,
        withFlag(context.currentExport.isEmpty) {
          exportNode :: Nil
        },
        Nil)

      (
        context.pushExport(exportNode),
        StatementWrapper(
          codeRange,
          rootExpression :: all,
          Nil))
    }
  }

  def ExportNamedDeclaration = ExportNamedDeclarationAsDecl | ExportNamedDeclarationAsVar | ExportNamedDeclarationAsSpec

  private def ExportDefaultDeclarationAsDecl = {
    node("ExportDefaultDeclaration") ~~
      constraint("declaration" -> opt(node("ClassDeclaration") | node("FunctionDeclaration")).mapValue(_.isDefined)) ~
      tup("declaration" -> (Declaration.ClassDeclaration | Declaration.FunctionDeclaration))
  } mapBoth {
    case (context, codeRange, declaration) => {
      withDefined(declaration.expressions.headOption) { expression =>
        val name = "default"

        val exportNode = context.getExport(codeRange)
        val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)

        val exportedEdge = CreateEdge(AnyNode(expression.node), exportKeyNode, ESPrimaEdgeType.Export).edge
        val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

        val wrapper = ExpressionWrapper(
          exportKeyNode,
          codeRange,
          Nil,
          withFlag(context.currentExport.isEmpty) {
            exportNode :: Nil
          },
          exportedEdge :: exportKeyEdge :: Nil)

        Option apply (
          context.pushExport(exportNode),
          StatementWrapper(
            codeRange,
            wrapper :: Nil,
            declaration :: Nil))
      } getOrElse {
        (context, declaration)
      }
    }
  }

  private def ExportDefaultDeclarationAsExpression = {
    node("ExportDefaultDeclaration") ~~
      constraint("declaration" -> opt(Expressions.expression).mapValue(_.isDefined)) ~
      tup("declaration" -> (Expressions.expression))
  } mapBoth {
    case (context, codeRange, expression) => {
      val name = "default"

      val exportNode = context.getExport(codeRange)
      val exportKeyNode = ExportKeyNode(Hashing.uuid, codeRange, name)

      val exportedEdge = CreateEdge(AnyNode(expression.node), exportKeyNode, ESPrimaEdgeType.Export).edge
      val exportKeyEdge = CreateEdge(exportKeyNode, exportNode, ESPrimaEdgeType.ExportKey).named(name)

      val wrapper = ExpressionWrapper(
        exportKeyNode,
        codeRange,
        Nil,
        withFlag(context.currentExport.isEmpty) {
          exportNode :: Nil
        },
        exportedEdge :: exportKeyEdge :: Nil)

      (
        context.pushExport(exportNode),
        StatementWrapper(
          codeRange,
          wrapper :: expression :: Nil,
          Nil))
    }
  }

  def ExportDefaultDeclaration = ExportDefaultDeclarationAsDecl | ExportDefaultDeclarationAsExpression

  val ExportAllDeclaration = {
    node("ExportAllDeclaration") ~
      tup("source" -> Terminal.Literal)
  } mapBoth {
    case (context, codeRange, source) => {
      val exportNode = context.getExport(codeRange)

      val requireNode = context.requireNode(source.node.range, source.node.value.as[String])

      val exportedEdge = CreateEdge(AnyNode(requireNode), exportNode, ESPrimaEdgeType.Export).edge

      val expression = ExpressionWrapper(
        requireNode,
        codeRange,
        Nil,
        withFlag(context.currentExport.isEmpty) {
          exportNode :: Nil
        },
        exportedEdge :: Nil)

      (
        context.pushExport(exportNode),
        StatementWrapper(
          codeRange,
          expression :: Nil,
          Nil))
    }
  }

  def statements = ExportNamedDeclaration | ExportAllDeclaration | ExportDefaultDeclaration
}
