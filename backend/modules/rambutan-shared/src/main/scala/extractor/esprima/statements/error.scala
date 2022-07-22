package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Error {

  // ExtractionWrapper
  private def CatchClause = {
    (node("CatchClause") ~
      tup("param" -> opt((Patterns.IdentifierPattern | Patterns.BindingPattern)))).mapContext {
        case (context, param) => {
          param.map { p =>
            context.pushIdentifiers(p.allIdentifiers)
          }.getOrElse(context)
        }
      } ~ tup("body" -> Statements.BlockStatement)
  } mapBoth {
    case (context, codeRange, (param, body)) => {
      val catchNode = CatchNode(Hashing.uuid, codeRange)

      // args
      val (catchArg, argEdge, patternExp) = param.map { p =>
        val catchArg = CatchArgNode(Hashing.uuid, p.codeRange)
        val argEdge = CreateEdge(catchNode, catchArg, ESPrimaEdgeType.FunctionArgument).edge
        val patternExp = p.apply(0, catchArg)

        (catchArg, argEdge, patternExp)
      } match {
        case Some((c, a, p)) => (Some(c), Some(a), p)
        case _               => (None, None, Nil)
      }

      // body
      val blockEdges = body.allNodes.map { bodyNode =>
        CreateEdge(catchNode, AnyNode(bodyNode), ESPrimaEdgeType.CatchContains).edge
      }

      (
        context.popIdentifiers,
        ExpressionWrapper(
          catchNode,
          codeRange,
          body.allExpressions ++ patternExp,
          catchArg.toList,
          argEdge.toList ++ blockEdges))
    }
  }

  def TryStatement = {
    node("TryStatement") ~
      tup("block" -> Statements.BlockStatement) ~
      tup("handler" -> opt(CatchClause)) ~
      tup("finalizer" -> opt(Statements.BlockStatement))
  } mapExtraction {
    case (context, codeRange, ((block, handler), finalizer)) => {
      val tryNode = TryNode(Hashing.uuid, codeRange)
      // link to TryNode
      // block.allNodes

      val tryEdges = block.allNodes.map { node =>
        CreateEdge(tryNode, AnyNode(node), ESPrimaEdgeType.TryContains).edge
      }

      val maybeCatchEdge = withDefined(handler) { h =>
        List(CreateEdge(tryNode, h.node, ESPrimaEdgeType.Catch).edge)
      }

      val maybeFinalizer = withDefined(finalizer) { f =>
        val finalizerNode = FinallyNode(Hashing.uuid, f.codeRange)
        // link to
        val bodyEdges = f.allNodes.map { node =>
          CreateEdge(finalizerNode, AnyNode(node), ESPrimaEdgeType.FinallyContains).edge
        }

        val edge = CreateEdge(tryNode, finalizerNode, ESPrimaEdgeType.Finally).edge
        ExpressionWrapper(
          finalizerNode,
          f.codeRange,
          Nil,
          Nil,
          edge :: bodyEdges) :: Nil
      }

      val tryExpression = ExpressionWrapper(
        tryNode,
        codeRange,
        Nil,
        Nil,
        maybeCatchEdge ++ tryEdges)

      StatementWrapper(
        codeRange,
        tryExpression :: (maybeFinalizer ++ handler.toList),
        block :: finalizer.toList)
    }
  }

  def ThrowStatement = {
    node("ThrowStatement") ~
      tup("argument" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, argument) => {
      val throwNode = ThrowNode(Hashing.uuid, codeRange)
      val throwEdge = CreateEdge(throwNode, AnyNode(argument.node), ESPrimaEdgeType.Throw).edge

      val throwExp = ExpressionWrapper(
        throwNode,
        codeRange,
        argument :: Nil,
        Nil,
        throwEdge :: Nil)

      StatementWrapper(codeRange, throwExp :: Nil, Nil)
    }
  }

  def statements = TryStatement | ThrowStatement
}