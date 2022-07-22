package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Loops {
  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#for-in
  private def For = {
    node("for") ~
      tup("children" -> ordered3(Expressions.Expression, Expressions.Expression, Statements.Statement)) mapExtraction {
        case (_, codeRange, (_, _, body)) => {
          val forNode = ForNode(Hashing.uuid, codeRange)

          ExpressionWrapper(
            forNode,
            codeRange,
            body.allExpressions,
            Nil,
            Nil)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#break
  // TODO: this has an expression attached
  private def Break = {
    node("break") mapExtraction {
      case (_, codeRange, _) => {
        ExpressionWrapper.single(BreakNode(Hashing.uuid, codeRange))
      }
    }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#next
  // TODO: this has an expression attached
  private def Next = {
    node("next") mapExtraction {
      case (_, codeRange, _) => {
        ExpressionWrapper.single(NextNode(Hashing.uuid, codeRange))
      }
    }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#redo
  private def Redo = {
    node("redo") mapExtraction {
      case (_, codeRange, _) => {
        ExpressionWrapper.single(RedoNode(Hashing.uuid, codeRange))
      }
    }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#with-precondition
  private def Until = {
    node("until") ~
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (_, codeRange, (condition, body)) => {
          val untilNode = UntilNode(Hashing.uuid, codeRange)
          val edges = List(
            CreateEdge(untilNode, AnyNode(condition.node), RubyEdgeType.LoopBody).edge,
            CreateEdge(untilNode, AnyNode(body.node), RubyEdgeType.LoopBody).edge)
          ExpressionWrapper(
            untilNode,
            codeRange,
            condition :: body :: Nil,
            Nil,
            edges)
        }
      }
  }

  private def While = {
    node("while") ~
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (_, codeRange, (condition, body)) => {
          val whileNode = WhileNode(Hashing.uuid, codeRange)
          val edges = List(
            CreateEdge(whileNode, AnyNode(condition.node), RubyEdgeType.LoopBody).edge,
            CreateEdge(whileNode, AnyNode(body.node), RubyEdgeType.LoopBody).edge)
          ExpressionWrapper(
            whileNode,
            codeRange,
            condition :: body :: Nil,
            Nil,
            edges)
        }
      }
  }

  def expressions = For | Break | Next | Redo | Until | While
}

