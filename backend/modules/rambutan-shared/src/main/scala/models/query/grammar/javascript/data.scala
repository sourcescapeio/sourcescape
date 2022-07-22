package models.query.grammar.javascript

import models.query.grammar._
import Base._

object DataIncomplete extends GrammarHelpers with CollectionHelpers {

  def ArrayExpressionIncomplete = {
    grammar("ArrayExpressionIncomplete")
      .payload(JavascriptPayloadType.ArrayExpression, "a" -> "a || []") {
        k("[") ~ named("a" -> ?(Data.ArrayList)) ~ ?(k(","))
      }
  }

  def ObjectAttributeIncomplete = {
    grammar("ObjectAttributeIncomplete")
      .payload(JavascriptPayloadType.ObjectAttribute) {
        named("i" -> Data.ObjectKey) ~ k(":") ~ named("e" -> ?(ExpressionsIncomplete.AnyExpression))
      }
  }

  def ObjectExpressionIncomplete = {
    grammar("ObjectExpressionIncomplete")
      .payload(JavascriptPayloadType.ObjectExpression) {
        named("o" -> incompleteListGrammar("ObjectExpressionIncompleteInner", pre = "{")(
          Data.ObjectAttribute,
          ObjectAttributeIncomplete))
      }
  }

}

object Data extends GrammarHelpers with CollectionHelpers {

  private def ObjectValue: Grammar = {
    grammar("ObjectValue")
      .single("e") {
        k(":") ~ __ ~ named("e" -> Expressions.Expression)
      }
  }

  def ObjectKey = {
    grammar("ObjectKey")
      .or(
        Ident.IdentifierExpression,
        Literal.String)
  }

  def ObjectAttribute = {
    grammar("ObjectAttribute")
      .payload(JavascriptPayloadType.ObjectAttribute) {
        named("i" -> ObjectKey) ~ __ ~ named("e" -> ?(ObjectValue))
      }
  }

  def ObjectAttributeList = {
    nonEmptyListGrammar("ObjectAttributeList", ObjectAttribute)
  }

  def ObjectExpression = {
    grammar("ObjectExpression")
      .payload(JavascriptPayloadType.ObjectExpression, "o" -> "o || []") {
        k("{") ~ __ ~ named("o" -> ?(ObjectAttributeList)) ~ __ ~ ?(k("}"))
      }
  }

  def ArrayList = {
    nonEmptyListGrammar("ArrayList", Expressions.Expression)
  }

  def ArrayExpression = {
    grammar("ArrayExpression")
      .payload(JavascriptPayloadType.ArrayExpression, "a" -> "a || []") {
        k("[") ~ __ ~ named("a" -> ?(ArrayList)) ~ __ ~ k("]")
      }
  }
}

