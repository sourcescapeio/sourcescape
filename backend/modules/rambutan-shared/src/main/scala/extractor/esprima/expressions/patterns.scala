package models.extractor.esprima

import models.CodeRange
import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Patterns {

  def applySpread(codeRange: CodeRange, argument: ExpressionWrapper[ESPrimaNodeBuilder]) = {
    val node = SpreadNode(Hashing.uuid, codeRange)
    val edge = CreateEdge(node, AnyNode(argument.node), ESPrimaEdgeType.Spread).edge
    ExpressionWrapper(
      node,
      codeRange,
      argument :: Nil,
      Nil,
      edge :: Nil)
  }

  def SpreadElement = {
    node("SpreadElement") ~
      tup("argument" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, argument) => {
      applySpread(codeRange, argument)
    }
  }

  // Patterns
  sealed case class PatternWrapper(
    codeRange:   CodeRange,
    identifiers: List[IdentifierNode],
    children:    List[PatternWrapper])(
    f: (Int, ESPrimaNodeBuilder) => List[ExpressionWrapper[ESPrimaNodeBuilder]]) {

    def allIdentifiers: List[IdentifierNode] = identifiers ++ children.flatMap(_.allIdentifiers)

    def apply(idx: Int, rootNode: ESPrimaNodeBuilder): List[ExpressionWrapper[ESPrimaNodeBuilder]] = {
      f(idx, rootNode) ++ children.zipWithIndex.flatMap {
        case (child, subIdx) => child.apply(subIdx, rootNode)
      }
    }
  }

  def IdentifierPattern = Terminal.Identifier.mapExtraction {
    case (context, codeRange, id) => {
      // drop everything
      // expressionwrapper?
      val ident = id.node.toIdentifier

      PatternWrapper(codeRange, ident :: Nil, Nil) { (idx: Int, root: ESPrimaNodeBuilder) =>
        val edge = CreateEdge(ident, AnyNode(root), ESPrimaEdgeType.Declare).edge
        ExpressionWrapper(
          ident,
          codeRange,
          Nil,
          Nil,
          edge :: Nil) :: Nil
      }
    }
  }

  // { a: b }
  def ObjectPattern: Extractor[ESPrimaContext, PatternWrapper] = {
    node("ObjectPattern") ~
      tup("properties" -> list(Property.Property or RestElement)) // TODO: RestElement
  } mapExtraction {
    case (context, codeRange, properties) => {

      val children = properties.flatMap(_.left.toOption).map { prop =>
        val maybeName = Property.computeName(prop.key.node)
        val memberNode = MemberNode(Hashing.uuid, prop.codeRange, maybeName)

        val (declNode, declEdge) = prop.value.map(_.node) match {
          case Some(in @ IdentifierNode(_, _, _)) => {
            (in, CreateEdge(in, AnyNode(memberNode), ESPrimaEdgeType.Declare).edge)
          }
          case Some(ir @ IdentifierReferenceNode(_, _, _)) => {
            val in = ir.toIdentifier
            (in, CreateEdge(in, AnyNode(memberNode), ESPrimaEdgeType.Declare).edge)
          }
          case un => throw new Exception(s"unknown declaration ${un}")
        }

        PatternWrapper(prop.codeRange, declNode :: Nil, Nil) { (idx: Int, root: ESPrimaNodeBuilder) =>

          val memberEdge = CreateEdge(
            memberNode,
            AnyNode(root),
            ESPrimaEdgeType.Member).named(maybeName.getOrElse(""))

          val maybeExp = Property.computeExpression(prop.key)

          // expression edge
          val memberKeyEdge = withDefined(maybeExp) { exp =>
            Option apply CreateEdge(
              memberNode,
              AnyNode(exp.node),
              ESPrimaEdgeType.MemberKey).edge
          }

          ExpressionWrapper(
            memberNode,
            prop.codeRange,
            maybeExp.toList,
            declNode :: Nil,
            memberEdge :: declEdge :: memberKeyEdge.toList) :: Nil
        }
      }

      PatternWrapper(codeRange, Nil, children) { (idx: Int, root: ESPrimaNodeBuilder) =>
        Nil
      }
    }
  }

  def ArrayPattern = {
    node("ArrayPattern") ~
      tup("elements" -> list(Patterns.PatternElement))
  } mapExtraction {
    case (context, codeRange, elements) => {
      val subs = elements.zipWithIndex.map {
        case (element, idx) => {
          PatternWrapper(element.codeRange, element.identifiers, Nil) { (idx: Int, root: ESPrimaNodeBuilder) =>
            val memberNode = MemberNode(Hashing.uuid, element.codeRange, Some(idx.toString))
            val memberEdge = CreateEdge(memberNode, AnyNode(root), ESPrimaEdgeType.Member).indexed(idx)

            val sub = element.apply(idx, root)

            ExpressionWrapper(
              memberNode,
              element.codeRange,
              sub,
              Nil,
              memberEdge :: Nil) :: Nil
          }
        }
      }

      PatternWrapper(codeRange, Nil, subs) { (idx: Int, root: ESPrimaNodeBuilder) =>
        Nil
      }
    }
  }

  def BindingPattern = {
    ArrayPattern | ObjectPattern
  }

  // def BindingName = {
  //   BindingPattern | Identifier
  // }

  // https://github.com/typescript-eslint/typescript-eslint/blob/e3836910/packages/typescript-estree/src/ts-estree/ts-estree.ts#L773
  def AssignmentPattern = {
    node("AssignmentPattern") ~
      tup("left" -> (IdentifierPattern | BindingPattern)) ~
      tup("right" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, (left, right)) => {
      PatternWrapper(codeRange, Nil, left :: Nil) { (idx: Int, root: ESPrimaNodeBuilder) =>
        val expression = left.apply(idx, root)
        // let's ignore defaults for now
        // val defaults = left.apply(idx, right.node) // how do we link defaults?
        context.logQueue.offer((codeRange, "Ignoring defaults for assignment\n" + left + "\n" + right))

        right :: Nil
      }
    }
  }

  // https://github.com/typescript-eslint/typescript-eslint/blob/e3836910/packages/typescript-estree/src/ts-estree/ts-estree.ts#L1171
  private def RestElement = {
    node("RestElement") ~
      tup("argument" -> (IdentifierPattern | BindingPattern))
  } mapExtraction {
    case (context, codeRange, argument) => {
      // like a slice
      // left, but inject default
      PatternWrapper(codeRange, Nil, argument :: Nil) { (idx: Int, root: ESPrimaNodeBuilder) =>
        context.logQueue.offer((codeRange, "Ignoring RestElement"))

        Nil
      }
    }
  }

  // https://github.com/typescript-eslint/typescript-eslint/blob/e3836910/packages/typescript-estree/src/ts-estree/ts-estree.ts#L1522
  private def TSParameterProperty = {
    node("TSParameterProperty") ~
      // accessibility
      // readonly
      // typeAnnotation
      tup("parameter" -> Patterns.PatternElement)
  } mapExtraction {
    case (context, codeRange, parameter) => {
      // we just pass through here. typeAnnotation is on the element
      parameter
    }
  }

  def PatternElement: Extractor[ESPrimaContext, PatternWrapper] = {
    AssignmentPattern | IdentifierPattern | BindingPattern | RestElement | TSParameterProperty
  }
}
