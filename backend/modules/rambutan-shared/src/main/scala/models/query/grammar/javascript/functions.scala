package models.query.grammar.javascript

import models.query._
import models.query.grammar._

import Base._

object FunctionsIncomplete extends GrammarHelpers {
  def FunctionIncompleteArguments = {
    grammar("FunctionIncompleteArguments")
      .single("args || []") {
        k("(") ~ __ ~ named("args" -> ?(Functions.FunctionArgumentsInner)) ~ __ ~ ?(k(","))
      }
  }

  def FunctionIncomplete = {
    grammar("FunctionIncomplete")
      .payload(JavascriptPayloadType.FunctionDefinition) {
        k("function") ~
          named("name" -> ?(Ident.DefinitionName)) ~ __ ~
          named("args" -> FunctionIncompleteArguments)
      }
  }
}

object Functions extends GrammarHelpers with CollectionHelpers {

  def FunctionArgumentsInner = {
    nonEmptyListGrammar("FunctionArgumentsInner", Ident.IdentifierExpression)
  }

  def FunctionArguments = {
    grammar("FunctionArguments")
      .single("args || []") {
        k("(") ~ named("args" -> ?(FunctionArgumentsInner)) ~ k(")")
      }
  }

  def FunctionDefinition = {
    grammar("FunctionDefinition")
      .payload(JavascriptPayloadType.FunctionDefinition) {
        k("function") ~
          named("name" -> ?(Ident.DefinitionName)) ~ __ ~
          named("args" -> ?(FunctionArguments)) ~ __ ~
          MaybeBrackets
      }
  }

  def FunctionInternalStatement = {
    grammar("FunctionInternalStatement")
      .or(
        ReturnStatement,
        YieldStatement,
        AwaitStatement,
      )
  }

  private def ReturnedExpression = {
    grammar("ReturnedExpression")
      .single("r") {
        ++(k(" ")) ~ named("r" -> ?(Expressions.Expression))
      }
  }

  private def retGrammar(id: String, payloadType: PayloadType, predicate: String) = {
    grammar(id)
      .payload(payloadType, "r" -> "r", "predicate" -> s"'${predicate}'") {
        k(predicate) ~ named("r" -> ?(ReturnedExpression))
      }
  }

  def ThrowStatement = {
    retGrammar("ThrowStatement", JavascriptPayloadType.ThrowStatement, "throw")
  }

  private def YieldStatement = {
    retGrammar("YieldStatement", JavascriptPayloadType.YieldStatement, "yield")
  }

  private def ReturnStatement = {
    retGrammar("ReturnStatement", JavascriptPayloadType.ReturnStatement, "return")
  }

  private def AwaitStatement = {
    retGrammar("AwaitStatement", JavascriptPayloadType.AwaitStatement, "await")
  }
}
