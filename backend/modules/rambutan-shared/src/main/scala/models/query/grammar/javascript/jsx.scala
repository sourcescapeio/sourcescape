package models.query.grammar.javascript

import models.query.grammar._
import Base._

object JSXIncomplete extends GrammarHelpers {

  private def JSXMaybeRef = {
    grammar("JSXMaybeRef")
      .payload(JavascriptPayloadType.JSXExpression) {
        k("<") ~ __ ~ named("ref" -> ?(Ident.RefOrIdent))
      }
  }

  def JSXAttributeIncomplete = {
    grammar("JSXAttributeIncomplete")
      .payload(JavascriptPayloadType.JSXAttribute) {
        named("i" -> Ident.IdentifierExpression) ~ k("=") ~ named("e" -> ?(ExpressionsIncomplete.AnyExpression))
      }
  }

  private def JSXSingleIncomplete = {
    grammar("JSXSingleIncomplete")
      .single("[attr]") {
        named("attr" -> JSXAttributeIncomplete)
      }
  }

  private def JSXIncompleteLast = {
    grammar("JSXIncompleteLast")
      .single("[...(attrs || []), ...(last ? [last] : [])]") {
        named("attrs" -> ?(JSX.JSXAttributeList)) ~ named("last" -> ?(JSXAttributeIncomplete))
      }
  }

  private def JSXAttrIncomplete = {
    grammar("JSXAttrIncomplete").or(
      JSXSingleIncomplete,
      JSXIncompleteLast)
  }

  private def JSXWithAttr = {
    grammar("JSXRefSingleIncomplete")
      .payload(JavascriptPayloadType.JSXExpression) {
        k("<") ~ __ ~ named("ref" -> Ident.RefOrIdent) ~ __ ~ named("attrs" -> ?(JSXAttrIncomplete))
      }
  }

  def JSXIncomplete = {
    grammar("JSXIncomplete").or(
      JSXWithAttr,
      JSXMaybeRef)
  }
}

object JSX extends GrammarHelpers with CollectionHelpers {

  private def JSXAttributeValue = {
    grammar("JSXAttributeValue")
      .single("e") {
        k("=") ~ named("e" -> ?(Expressions.Expression))
      }
  }

  def JSXAttribute = {
    grammar("JSXAttribute")
      .payload(JavascriptPayloadType.JSXAttribute) {
        named("i" -> Ident.IdentifierExpression) ~ named("e" -> ?(JSXAttributeValue))
      }
  }

  def JSXAttributeList = {
    nonEmptyListGrammar("JSXAttributeList", JSXAttribute, divider = grouped(++(k(" "))))
  }

  def JSXExpression = {
    grammar("JSXExpression")
      .payload(JavascriptPayloadType.JSXExpression) {
        k("<") ~ named("ref" -> Ident.RefOrIdent) ~ __ ~ named("attrs" -> ?(JSXAttributeList)) ~ __ ~ ?(multi(k("/>"), k("/")))
      }
  }
}
