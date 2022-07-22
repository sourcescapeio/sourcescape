package models.query.grammar.ruby

import models.query.grammar._
import Base._

object DataIncomplete extends GrammarHelpers with CollectionHelpers {

  def ArrayExpressionIncomplete = {
    grammar("ArrayExpressionIncomplete")
      .payload(RubyPayloadType.ArrayExpression, "a" -> "a || []") {
        k("[") ~ named("a" -> ?(Data.ArrayList)) ~ ?(k(","))
      }
  }

  private def HashValueIncomplete: Grammar = {
    grammar("HashValueIncomplete")
      .single("e") {
        multi(k("=>"), k("=")) ~ __ ~ named("e" -> ?(ExpressionsIncomplete.AnyExpression))
      }
  }

  def HashPairIncomplete: Grammar = {
    grammar("HashPairIncomplete")
      .payload(RubyPayloadType.HashPair) {
        named("i" -> Data.HashKey) ~ __ ~ named("e" -> ?(HashValueIncomplete))
      }
  }

  //{ <rep(full_pair)>,  }
  def HashExpressionIncomplete = {
    grammar("HashExpressionIncomplete")
      .payload(RubyPayloadType.HashExpression) {
        named("o" -> incompleteListGrammar("HashExpressionIncompleteInner", pre = "{")(
          Data.HashPair,
          HashPairIncomplete))
      }
  }
}

object Data extends GrammarHelpers with CollectionHelpers {

  def ArrayList = {
    nonEmptyListGrammar("ArrayList", Expressions.BasicExpression)
  }

  def ArrayExpression = {
    grammar("ArrayExpression")
      .payload(RubyPayloadType.ArrayExpression, "a" -> "a || []") {
        k("[") ~ __ ~ named("a" -> ?(ArrayList)) ~ __ ~ k("]")
      }
  }

  //
  private def HashValue: Grammar = {
    grammar("HashValue")
      .single("e") {
        k("=>") ~ __ ~ named("e" -> Expressions.BasicExpression)
      }
  }

  def HashKey = {
    grammar("HashKey")
      .or(
        Ident.IdentifierExpression, // different from javascript, this is like a grouped search
        Literal.String)
  }

  def HashPair = {
    grammar("HashPair")
      .payload(RubyPayloadType.HashPair) {
        named("i" -> HashKey) ~ __ ~ named("e" -> ?(HashValue))
      }
  }

  def HashPairList = {
    nonEmptyListGrammar("HashPairList", HashPair)
  }

  def HashExpression = {
    grammar("HashExpression")
      .payload(RubyPayloadType.HashExpression, "o" -> "o || []") {
        k("{") ~ __ ~ named("o" -> ?(HashPairList)) ~ __ ~ k("}")
      }
  }
}
