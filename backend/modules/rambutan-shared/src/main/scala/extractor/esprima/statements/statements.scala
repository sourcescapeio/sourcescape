package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Statements {

  def ExpressionStatement = {
    node("ExpressionStatement") ~
      tup("expression" -> Expressions.expression)
  } mapExtraction {
    case (context, codeRange, expression) => {
      StatementWrapper(
        codeRange,
        expression :: Nil,
        Nil)
    }
  }

  val EmptyStatement = node("EmptyStatement") mapExtraction {
    case (context, codeRange, expression) => {
      StatementWrapper.empty(codeRange)
    }
  }

  val DebuggerStatement = node("DebuggerStatement") mapExtraction {
    case (context, codeRange, expression) => {
      StatementWrapper.empty(codeRange)
    }
  }

  def Statement: ChainedExtractor[ESPrimaContext, StatementWrapper] = {
    ExpressionStatement | EmptyStatement | Statements.BlockStatement | Control.statements | Error.statements | Loop.statements | Import.statements | Export.statements | Declaration.VariableDeclaration
  }

  def StatementListItem = {
    Declaration.statements | Statement
  }

  def BlockStatement = {
    node("BlockStatement").mapExtraction {
      case (context, codeRange, ()) => context // store context to restore later
    } ~
      tup("body" -> sequence(StatementListItem))
  } mapBoth {
    case (context, codeRange, (savedContext, body)) => {
      (savedContext, StatementWrapper(codeRange, Nil, body))
    }
  }
}
