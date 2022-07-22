package models.extractor.esprima

import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Functions {
  val ArgumentListElement = {
    Expressions.expression | Patterns.SpreadElement
  }

  def AwaitExpression = {
    node("AwaitExpression") ~
      tup("argument" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, argument) => {
      val node = AwaitNode(Hashing.uuid, codeRange)
      val edge = CreateEdge(node, AnyNode(argument.node), ESPrimaEdgeType.Await).edge
      ExpressionWrapper(
        node,
        codeRange,
        argument :: Nil,
        Nil,
        edge :: Nil)
    }
  }

  def YieldExpression = {
    node("YieldExpression") ~
      tup("argument" -> opt(Expressions.expression)) ~
      tup("delegate" -> Extractor.bool) // wtf is this?
  } mapExtraction {
    case (context, codeRange, (argument, delegate)) => {
      val node = YieldNode(Hashing.uuid, codeRange)
      val maybeEdge = withDefined(argument) { arg =>
        List(CreateEdge(node, AnyNode(arg.node), ESPrimaEdgeType.Yield).edge)
      }
      ExpressionWrapper(
        node,
        codeRange,
        argument.toList,
        Nil,
        maybeEdge)
    }
  }

  def CallExpression = {
    node("CallExpression") ~
      tup("callee" -> Expressions.expression) ~ // or Import
      tup("arguments" -> list(ArgumentListElement))
  } mapExtraction {
    case (context, codeRange, (callee, arguments)) => {
      (callee.node, arguments.map(_.node)) match {
        case (IdentifierReferenceNode(_, _, "require"), List(LiteralNode(_, _, _, module))) => {
          ExpressionWrapper.single(
            context.requireNode(codeRange, module.as[String]))
        }
        case _ => {
          val call = CallNode(Hashing.uuid, codeRange)
          val edge = CreateEdge(
            call,
            AnyNode(callee.node),
            ESPrimaEdgeType.Call).edge

          val argumentEdges = arguments.zipWithIndex.map {
            case (arg, idx) => {
              CreateEdge(
                AnyNode(arg.node),
                call,
                ESPrimaEdgeType.Argument).indexed(idx)
            }
          }

          ExpressionWrapper(
            call,
            codeRange,
            callee :: arguments,
            Nil,
            edge :: argumentEdges)
        }
      }
    }
  }

  // This unifies a lot of cases (ex: FunctionExpression does not have an Expression body)
  // But should be safe.
  // Trading off safety for simplicity
  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L627
  type FunctionType = Extractor[ESPrimaContext, ExpressionWrapper[FunctionNode]]
  def extractFunction(name: String): FunctionType = {
    (node(name) ~
      tup("id" -> opt(Terminal.Identifier)) ~ // not sure if this identifier really does anything for expressions
      // returnType
      // typeParameters
      tup("params" -> list(Patterns.PatternElement))).mapContext {
        case (context, (id, params)) => {
          context.pushIdentifiers(params.flatMap(_.identifiers))
        }
      } ~
      tup("body" -> (Statements.BlockStatement or Expressions.expression)) ~
      tup("generator" -> Extractor.bool) ~
      tup("async" -> Extractor.bool)
  } mapBoth {
    case (context, codeRange, ((((id, params), body), generator), async)) => {
      // discard edges
      val maybeIdentifier = id.map(_.node).map(_.toIdentifier)
      val maybeName = maybeIdentifier.map(_.name)
      val functionNode = FunctionNode(Hashing.uuid, codeRange, async, maybeName)

      // attach function to identifier
      val maybeIdentEdge = withDefined(maybeIdentifier) { ident =>
        List(CreateEdge(ident, functionNode, ESPrimaEdgeType.Declare).edge)
      }

      // params
      val paramComputed = params.zipWithIndex.map {
        case (param, idx) => {
          val arg = FunctionArgNode(Hashing.uuid, param.codeRange, idx)
          val argEdge = CreateEdge(functionNode, arg, ESPrimaEdgeType.FunctionArgument).indexed(idx)
          val paramExp = param.apply(idx, arg)
          (arg, argEdge, paramExp)
        }
      }
      val argNodes: List[ESPrimaNodeBuilder] = paramComputed.map(_._1)
      val argEdges: List[ESPrimaEdgeBuilder] = paramComputed.map(_._2)
      val paramExpressions: List[ExpressionWrapper[ESPrimaNodeBuilder]] = {
        paramComputed.flatMap(_._3)
      }

      // REVERTED: in determining body, we do not recurse into child function definitions
      // Reverting for now because this is causing queries to hang
      // .allNodesLimitingFunction
      val (bodyNodes, allExpressions) = body match {
        case Left(stmt) => {
          (stmt.allNodes, stmt.allExpressions)
        }
        case Right(exp) => {
          (exp.allNodes, List(exp))
        }
      }

      // find return
      val (returnNode, returnEdges) = body match {
        case Left(stmt) => {
          val edges = stmt.allNodes.flatMap {
            case r @ ReturnNode(_, _) => Some(CreateEdge(r, functionNode, ESPrimaEdgeType.FunctionReturn).edge)
            case y @ YieldNode(_, _)  => Some(CreateEdge(y, functionNode, ESPrimaEdgeType.FunctionReturn).edge)
            case a @ AwaitNode(_, _)  => Some(CreateEdge(a, functionNode, ESPrimaEdgeType.FunctionReturn).edge)
            case _                    => None
          }
          (None, edges)
        }
        case Right(exp) => {
          val ret = ReturnNode(Hashing.uuid, exp.codeRange)
          val edges = List(
            CreateEdge(ret, AnyNode(exp.node), ESPrimaEdgeType.Return).edge,
            CreateEdge(ret, functionNode, ESPrimaEdgeType.FunctionReturn).edge)
          (Some(ret), edges)
        }
      }

      val bodyEdges = bodyNodes.map { bodyNode =>
        CreateEdge(functionNode, AnyNode(bodyNode), ESPrimaEdgeType.FunctionContains).edge
      }

      val wrapper = ExpressionWrapper(
        functionNode,
        codeRange,
        paramExpressions ++ allExpressions,
        argNodes ++ maybeIdentifier.toList ++ returnNode.toList,
        bodyEdges ++ argEdges ++ returnEdges ++ maybeIdentEdge)

      val nextContext = maybeIdentifier.foldLeft(context.popIdentifiers)(_ declareIdentifier _)
      (nextContext, wrapper)
    }
  }

  // TODO: it is unclear why I need to pass the node in
  // instead of just the name
  def TSEmptyBodyFunctionExpression = {
    extractFunction("TSEmptyBodyFunctionExpression")
  }

  def ArrowFunctionExpression = {
    extractFunction("ArrowFunctionExpression")
  }

  def FunctionExpression = {
    extractFunction("FunctionExpression")
  }

  def expressions = {
    ArrowFunctionExpression | FunctionExpression | CallExpression | AwaitExpression | YieldExpression
  }
}
