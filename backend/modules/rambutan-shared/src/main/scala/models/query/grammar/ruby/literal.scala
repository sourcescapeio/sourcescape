package models.query.grammar.ruby

import models.query.grammar._

object Literal extends LiteralHelpers {
  def Literal = {
    grammar("Literal")
      .or(
        Keyword,
        Number,
        String)
  }

  val Keywords = List("true", "false", "nil")
  private def Keyword = {
    keywordLiteral(Keywords, RubyPayloadType.KeywordLiteral)
  }

  private def Number = {
    numberLiteral(RubyPayloadType.NumberLiteral)
  }

  def String = {
    stringLiteral(RubyPayloadType.StringLiteral)
  }
}

object LiteralIncomplete extends LiteralHelpers {

  def StringIncomplete = {
    incompleteStringLiteral(RubyPayloadType.StringLiteral)
  }

}