package models.query.grammar.ruby

import models.query.grammar._
import Base._

object Expressions extends GrammarHelpers {

  // composites
  def BasicExpression: Grammar = {
    grammar("BasicExpression").or(
      Literal.Literal,
      Data.ArrayExpression,
      Data.HashExpression,
      Traverses.RootedExpression)
  }

  def Expression = BasicExpression

}

object ExpressionsIncomplete extends GrammarHelpers {
  def BasicExpressionIncomplete: Grammar = {
    grammar("BasicExpressionIncomplete").or(
      DataIncomplete.HashExpressionIncomplete,
      DataIncomplete.ArrayExpressionIncomplete,
      LiteralIncomplete.StringIncomplete,
      TraversesIncomplete.IncompleteSendRoot,
      TraversesIncomplete.IncompleteRootedExpression,
      TraversesIncomplete.IncompleteConstExpression)
  }

  def ExpressionIncomplete: Grammar = {
    BasicExpressionIncomplete
    // grammar("ExpressionIncomplete").or(
    //   BasicExpressionIncomplete,
    //   FunctionsIncomplete.FunctionIncomplete,
    //   JSXIncomplete.JSXIncomplete)
  }

  def AnyExpression: Grammar = {
    grammar("AnyExpression").or(
      Expressions.Expression,
      ExpressionIncomplete)
  }
}
