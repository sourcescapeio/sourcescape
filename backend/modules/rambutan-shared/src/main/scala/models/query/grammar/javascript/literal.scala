package models.query.grammar.javascript

import models.query.grammar._

object Literal extends LiteralHelpers {
  def Literal = {
    grammar("Literal")
      .or(
        Keyword,
        Number,
        String)
  }

  val Keywords = List("true", "false", "null", "undefined", "NaN")
  private def Keyword = {
    keywordLiteral(Keywords, JavascriptPayloadType.Keyword)
  }

  private def Number = {
    numberLiteral(JavascriptPayloadType.Number)
  }

  def String = {
    stringLiteral(JavascriptPayloadType.String)
  }
}

object LiteralIncomplete extends LiteralHelpers {
  def StringIncomplete = {
    incompleteStringLiteral(JavascriptPayloadType.String)
  }
}
