package models.query.grammar.javascript

import models.query.grammar._
import models.index.esprima.CanIndex.BasicExpression

object ExpressionsIncomplete extends GrammarHelpers {
  def AnyBasicExpression: Grammar = {
    grammar("AnyBasicExpression").or(
      BasicExpressionIncomplete,
      Expressions.BasicExpression)
  }

  def AnyExpression: Grammar = {
    grammar("AnyExpression").or(
      Expressions.Expression,
      ExpressionIncomplete) // Expressions are more specific, should be before incompletes
  }

  def ExpressionIncomplete: Grammar = {
    grammar("ExpressionIncomplete").or(
      BasicExpressionIncomplete,
      FunctionsIncomplete.FunctionIncomplete,
      JSXIncomplete.JSXIncomplete)
  }

  def BasicExpressionIncomplete: Grammar = {
    grammar("BasicExpressionIncomplete").or(
      DataIncomplete.ObjectExpressionIncomplete,
      DataIncomplete.ArrayExpressionIncomplete,
      LiteralIncomplete.StringIncomplete,
      IdentIncomplete.DepIncomplete,
      TraversesIncomplete.RootedIncomplete,
      CondIncomplete.UnaryIncomplete,
      CondIncomplete.BinaryIncomplete,
      SimpleIncomplete.TemplateIncomplete)
  }
}

object Expressions extends GrammarHelpers {

  def BasicExpression: Grammar = {
    grammar("BasicExpression").or(
      Ident.DepExpression,
      Cond.UnaryExpression,
      Cond.BinaryExpression,
      Literal.Literal,
      Data.ObjectExpression,
      Data.ArrayExpression,
      Traverses.RootedExpression,
      Simple.TemplateExpression)
  }

  def Expression: Grammar = {
    grammar("Expression")
      .or(
        BasicExpression,
        Functions.FunctionDefinition,
        JSX.JSXExpression)
  }
}
