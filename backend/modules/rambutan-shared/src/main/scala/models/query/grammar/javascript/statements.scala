package models.query.grammar.javascript

import models.query.grammar._
import akka.protobufv3.internal.Internal

object StatementsIncomplete extends GrammarHelpers {

  def InternalStatementIncomplete = {
    grammar("InternalStatementIncomplete").or(
      CondIncomplete.IfIncomplete)
  }

  def StatementIncomplete = {
    grammar("StatementIncomplete").or(
      ClassesIncomplete.ClassIncomplete,
      InternalStatementIncomplete)
  }
}

object Statements extends GrammarHelpers {

  def InternalStatement = {
    grammar("InternalStatement").or(
      Functions.ThrowStatement,
      Cond.IfStatement)
  }

  def Statement = {
    grammar("Statement")
      .or(
        Classes.ClassDefinition,
        InternalStatement)
  }
}
