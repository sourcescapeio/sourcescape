package models.query.grammar.javascript

import models.query.grammar._
import Base._

object SimpleIncomplete extends GrammarHelpers {

  private def openQuote = multi(k("'"), k("\\\""))

  private def RequireNoArg = {
    grammar("RequireNoArg")
      .payload(JavascriptPayloadType.Require) {
        k("require") ~ ?(grouped(k("(") ~ ?(openQuote)))
      }
  }

  private def RequirePartialArg = {
    grammar("RequirePartialArg")
      .payload(JavascriptPayloadType.Require, "name" -> "name.name") {
        k("require(") ~ openQuote ~ named("name" -> Ident.Identifier)
      }
  }

  private def RequireOpenParens = {
    grammar("RequireOpenParens")
      .payload(JavascriptPayloadType.Require, "name" -> "name.name") {
        k("require(") ~ named("name" -> Literal.String)
      }
  }

  def RequireIncomplete = {
    grammar("RequireIncomplete").or(
      RequireOpenParens,
      RequirePartialArg,
      RequireNoArg)
  }

  private def TemplateComponentIncomplete = {
    grammar("TemplateComponentIncomplete")
      .payload(JavascriptPayloadType.TemplateComponent) {
        k("${") ~ named("expression" -> ExpressionsIncomplete.AnyBasicExpression)
      }
  }

  def TemplateIncomplete = {
    grammar("TemplateIncomplete")
      .payload(JavascriptPayloadType.Template, "components" -> "components.concat(last ? [last] : [])") {
        k("`") ~ named("components" -> *(Simple.TemplateInner)) ~ named("last" -> ?(TemplateComponentIncomplete))
      }
  }
}

object Simple extends GrammarHelpers {

  /**
   * Requires
   */
  def RequireExpression = {
    grammar("RequireExpression")
      .payload(JavascriptPayloadType.Require, "name" -> "name.name") {
        k("require(") ~ named("name" -> Literal.String) ~ k(")")
      }
  }

  /**
   * Templates
   */
  private def TemplateString = {
    grammar("TemplateString")
      .payload(JavascriptPayloadType.TemplateComponent, "raw" -> "text()") {
        pattern("[^`^$]+")
      }
  }

  private def TemplateComponent = {
    grammar("TemplateComponent")
      .payload(JavascriptPayloadType.TemplateComponent) {
        k("${") ~ named("expression" -> Expressions.BasicExpression) ~ k("}")
      }
  }

  def TemplateInner = {
    grammar("TemplateInner")
      .or(
        TemplateString,
        TemplateComponent)
  }

  def TemplateExpression = {
    grammar("TemplateExpression")
      .payload(JavascriptPayloadType.Template) {
        k("`") ~ named("components" -> *(TemplateInner)) ~ k("`")
      }
  }

}
