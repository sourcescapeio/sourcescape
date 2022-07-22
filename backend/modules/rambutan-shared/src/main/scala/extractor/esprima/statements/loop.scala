package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Loop {

  private def whileNode(in: NodeExtractor[ESPrimaContext, Unit], doWhile: Boolean) = {
    in ~
      tup("test" -> Expressions.expression) ~
      tup("body" -> Statements.Statement)
  } mapExtraction {
    case (context, codeRange, (test, body)) => {
      val whileNode = WhileNode(Hashing.uuid, codeRange, doWhile)
      // whileTest

      val testEdge = CreateEdge(whileNode, AnyNode(test.node), ESPrimaEdgeType.WhileTest).edge

      val blockEdges = body.allNodes.map { node =>
        CreateEdge(whileNode, AnyNode(node), ESPrimaEdgeType.WhileContains).edge
      }

      val exp = ExpressionWrapper(
        whileNode,
        codeRange,
        test :: Nil,
        Nil,
        testEdge :: blockEdges)

      StatementWrapper(
        codeRange,
        exp :: Nil,
        body :: Nil)
    }
  }

  def WhileStatement = {
    whileNode(node("WhileStatement"), doWhile = false)
  }

  def DoWhileStatement = {
    whileNode(node("DoWhileStatement"), doWhile = true)
  }

  def ForStatement = {
    // how to inject [init]
    (node("ForStatement") ~
      tup("init" -> opt(Declaration.VariableDeclaration or Expressions.expression))).mapContext {
        case (context, init) => {
          val identifiers = withDefined(init) {
            case Left(decl) => decl.allExpressions.flatMap(_.allIdentifiers)
            case Right(exp) => exp.allIdentifiers
          }
          context.pushIdentifiers(identifiers)
        }
      } ~
      tup("test" -> opt(Expressions.expression)) ~
      tup("update" -> opt(Expressions.expression)) ~
      tup("body" -> Statements.Statement)
  } mapBoth {
    case (context, codeRange, (((init, test), update), body)) => {
      val forNode = ForNode(Hashing.uuid, codeRange)

      val initSub = withDefined(init) {
        case Left(p)    => p :: Nil
        case Right(exp) => StatementWrapper(codeRange, exp :: Nil, Nil) :: Nil
      }

      val testEdge = withDefined(test) { t =>
        List(CreateEdge(forNode, AnyNode(t.node), ESPrimaEdgeType.ForTest).edge)
      }
      val updateEdge = withDefined(update) { u =>
        List(CreateEdge(forNode, AnyNode(u.node), ESPrimaEdgeType.ForUpdate).edge)
      }

      val exp = ExpressionWrapper(
        forNode,
        codeRange,
        test.toList ++ update.toList,
        Nil,
        testEdge ++ updateEdge)

      (
        context.popIdentifiers,
        StatementWrapper(
          codeRange,
          exp :: Nil,
          body :: initSub))
    }
  }

  private def extractFor(in: NodeExtractor[ESPrimaContext, Unit]) = {
    // how to inject [left]
    (in ~ tup("left" -> (Declaration.LoopVariableDeclaration or Terminal.Identifier))).mapContext {
      case (context, Left(decl)) => context.pushIdentifiers(decl.flatMap(_.identifiers))
      case (context, Right(exp)) => context.pushIdentifiers(exp.allIdentifiers)
    } ~
      tup("right" -> Expressions.expression) ~
      tup("body" -> Statements.Statement)
  } mapBoth {
    case (context, codeRange, ((left, right), body)) => {
      val forNode = ForNode(Hashing.uuid, codeRange)

      val loopVarCodeRange = left match {
        case Left(l)  => l.head.codeRange
        case Right(r) => r.codeRange
      }

      val loopVar = LoopVarNode(Hashing.uuid, loopVarCodeRange) // loop var link
      val loopEdge = CreateEdge(loopVar, AnyNode(right.node), ESPrimaEdgeType.ForInit).edge

      val varExp = left match {
        case Left(l) => {
          ExpressionWrapper(
            loopVar,
            loopVarCodeRange,
            l.flatMap(_.apply(0, loopVar)),
            Nil,
            loopEdge :: Nil)
        }
        case Right(id) => {
          val node = BinaryExpressionNode(Hashing.uuid, id.codeRange, "=")
          val argEdges = List(
            CreateEdge(AnyNode(id.node), node, ESPrimaEdgeType.BasicExpression).indexed(0),
            CreateEdge(AnyNode(loopVar), node, ESPrimaEdgeType.BasicExpression).indexed(1))

          ExpressionWrapper(
            node,
            id.codeRange,
            Nil,
            loopVar :: Nil,
            loopEdge :: argEdges)
        }
      }

      // body block
      val loopVarEdge = CreateEdge(forNode, loopVar, ESPrimaEdgeType.ForVar).edge
      val blockEdges = body.allNodes.map { node =>
        CreateEdge(forNode, AnyNode(node), ESPrimaEdgeType.ForContains).edge
      }

      val exp = ExpressionWrapper(
        forNode,
        codeRange,
        varExp :: right :: Nil,
        Nil,
        loopVarEdge :: blockEdges)

      (
        context.popIdentifiers,
        StatementWrapper(
          codeRange,
          exp :: Nil,
          body :: Nil))
    }
  }

  def ForInStatement = {
    extractFor(node("ForInStatement"))
  }

  def ForOfStatement = {
    extractFor(node("ForOfStatement"))
  }

  def statements = {
    WhileStatement | DoWhileStatement | ForStatement | ForInStatement | ForOfStatement
  }

}