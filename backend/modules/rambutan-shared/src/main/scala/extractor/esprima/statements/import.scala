package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Import {

  // https://github.com/estree/estree/blob/master/es2015.md#imports
  private val ImportSpecifier = {
    node("ImportSpecifier") ~
      tup("local" -> Terminal.Identifier) ~
      tup("imported" -> Terminal.Identifier)
  } mapExtraction {
    case (context, codeRange, (local, imported)) => {
      val member = MemberNode(Hashing.uuid, codeRange, Some(imported.node.name))
      (local.node.toIdentifier, Some(member))
    }
  }

  private val ImportDefaultSpecifier = {
    node("ImportDefaultSpecifier") ~
      tup("local" -> Terminal.Identifier)
  } mapExtraction {
    case (context, codeRange, local) => {
      val member = MemberNode(Hashing.uuid, codeRange, Some("default"))
      (local.node.toIdentifier, Some(member))
    }
  }

  private val ImportNamespaceSpecifier = {
    node("ImportNamespaceSpecifier") ~
      tup("local" -> Terminal.Identifier)
  } mapExtraction {
    case (context, codeRange, local) => {
      (local.node.toIdentifier, None)
    }
  }

  private def ImportSpecifierExpression = ImportSpecifier | ImportDefaultSpecifier | ImportNamespaceSpecifier

  def ImportDeclaration = {
    node("ImportDeclaration") ~
      tup("source" -> Terminal.Literal) ~
      tup("specifiers" -> list(ImportSpecifierExpression))
  } mapBoth {
    case (context, codeRange, (source, specifiers)) => {
      val requireNode = context.requireNode(source.node.range, source.node.value.as[String])
      // create edge

      val nodes = specifiers.flatMap {
        case (id, Some(member)) => id :: member :: Nil
        case (id, None)         => id :: Nil
      }
      val edges = specifiers.flatMap {
        case (id, Some(member)) => {
          List(
            CreateEdge(member, AnyNode(requireNode), ESPrimaEdgeType.Member).named(member.name.getOrElse("")),
            CreateEdge(id, member, ESPrimaEdgeType.Declare).edge)
        }
        case (id, None) => {
          CreateEdge(id, requireNode, ESPrimaEdgeType.Declare).edge :: Nil
        }
      }

      val expression = ExpressionWrapper(
        requireNode,
        codeRange,
        Nil,
        nodes,
        edges)

      (
        context.declareIdentifiers(specifiers.map(_._1)),
        StatementWrapper(
          codeRange,
          expression :: Nil,
          Nil))
    }
  }

  def statements = ImportDeclaration

}