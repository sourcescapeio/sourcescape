package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._
import scala.meta.Stat

object Cond {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#defined
  // TODO: pass through for now
  private def DefinedQ = {
    node("defined?") ~
      tup("children" -> ordered1(Expressions.Expression)) mapExtraction {
        case (context, codeRange, exp) => {
          exp
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#branching
  private def If = {
    node("if") ~
      tup("children" -> ordered3(Expressions.Expression, opt(Statements.Statement), opt(Statements.Statement))) mapExtraction {
        case (context, codeRange, (cond, thenBlock, elseBlock)) => {
          // TODO: pull full name, maybe?
          val ifNode = IfNode(Hashing.uuid, codeRange)
          // edges
          ExpressionWrapper(
            ifNode,
            codeRange,
            cond :: (thenBlock.toList.flatMap(_.allExpressions) ++ elseBlock.toList.flatMap(_.allExpressions)),
            Nil,
            Nil)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#when-clause
  // TODO: fix, we're just blurring everything together here
  private def When = {
    node("when") ~
      tup("children" -> list(Statements.Statement))
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#case-expression-clause
  private def Case = {
    node("case") ~
      tup("children" -> ordered1(opt(Expressions.Expression)).and {
        list(either(When, opt(Statements.Statement))) // need to deal with else!
      }) mapExtraction {
        case (context, codeRange, (caseClause, whens)) => {
          val caseNode = CaseNode(Hashing.uuid, codeRange)

          val flattenedStatements = whens flatMap {
            case Left(rbody) => rbody
            case Right(s)    => s.toList
          }

          // case body
          ExpressionWrapper(
            caseNode,
            codeRange,
            flattenedStatements.flatMap(_.allExpressions),
            Nil,
            Nil)
        }
      }

  }

  def expressions = If | Case | DefinedQ
}
