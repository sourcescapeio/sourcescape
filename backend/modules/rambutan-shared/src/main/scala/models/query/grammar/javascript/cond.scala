package models.query.grammar.javascript

import models.query.grammar._
import Base._

object CondIncomplete extends GrammarHelpers {
  def UnaryIncomplete = {
    grammar("UnaryIncomplete")
      .payload(JavascriptPayloadType.UnaryExpression) {
        named("op" -> Cond.PrefixUnaryOperator)
      }
  }

  private def TextBinaryOperatorIncomplete = {
    grammar("TextBinaryOperatorIncomplete")
      .single("op") {
        ++(k(" ")) ~ named("op" -> enums(Cond.TextBinaryOperators))
      }
  }

  private def BinaryOperatorIncomplete = {
    grammar("BinaryOperatorIncomplete").or(
      Cond.BinaryOperator,
      TextBinaryOperatorIncomplete)
  }

  private def BinaryWithRight: Grammar = {
    grammar("BinaryWithRight")
      .payload(JavascriptPayloadType.BinaryExpression) {
        k("(") ~ __ ~
          named("a" -> Expressions.BasicExpression) ~
          named("op" -> Cond.BinaryOperator) ~
          named("b" -> ?(ExpressionsIncomplete.AnyBasicExpression))
      }
  }
  private def BinaryOnlyLeft = {
    grammar("BinaryOnlyLeft")
      .payload(JavascriptPayloadType.BinaryExpression) {
        k("(") ~ __ ~
          named("a" -> ExpressionsIncomplete.AnyBasicExpression) ~
          named("op" -> ?(BinaryOperatorIncomplete))
      }
  }

  def BinaryIncomplete = {
    grammar("BinaryIncomplete").or(
      BinaryWithRight,
      BinaryOnlyLeft)
  }

  private def IfConditionOpenParens = {
    grammar("IfConditionOpenParens")
      .single("c") {
        k("(") ~ named("c" -> ?(ExpressionsIncomplete.AnyBasicExpression)) ~ __
      }
  }

  private def IfConditionIncomplete = {
    grammar("IfConditionIncomplete").or(
      IfConditionOpenParens,
      BinaryIncomplete)
  }

  def IfIncomplete = {
    grammar("IfIncomplete")
      .payload(JavascriptPayloadType.IfStatement) {
        k("if") ~ __ ~ named("c" -> ?(IfConditionIncomplete))
      }
  }
}

object Cond extends GrammarHelpers {
  val PostFixUnaryOperators = List("++", "--")

  private def PostFixUnaryOperator = {
    grammar("PostFixUnaryOperator")
      .onlyPattern {
        enums(PostFixUnaryOperators)
      }
  }

  private def UnaryExpressionPostfix = {
    grammar("UnaryExpressionPostfix")
      .payload(JavascriptPayloadType.UnaryExpression) {
        k("(") ~ __ ~ named("a" -> Expressions.BasicExpression) ~ __ ~ k(")") ~ named("op" -> PostFixUnaryOperator)
      }
  }

  val PrefixUnaryOperators = List("typeof", "delete", "void", "[?]", "!")
  def PrefixUnaryOperator = {
    grammar("PreFixUnaryOperator")
      .onlyPattern {
        enums(PostFixUnaryOperators ++ PrefixUnaryOperators)
      }
  }

  private def UnaryExpressionPrefix = {
    grammar("UnaryExpressionPrefix")
      .payload(JavascriptPayloadType.UnaryExpression, "op" -> "(op === '[?]') ? null : op", "a" -> "a") {
        named("op" -> PrefixUnaryOperator) ~ __ ~ named("a" -> Expressions.BasicExpression)
      }
  }

  def UnaryExpression = {
    grammar("UnaryExpression")
      .or(
        UnaryExpressionPrefix,
        UnaryExpressionPostfix)
  }

  val TextBinaryOperators = List("instanceof", "in")
  val OtherBinaryOperators = List(
    "[?]", "===", "==", "!==", "!=", "&&", "||",
    "%", "^", ">>>", ">>", "<<", "&", "|")
  private def TextBinaryOperator = {
    grammar("TextBinaryOperator")
      .single("op") {
        ++(k(" ")) ~ named("op" -> enums(TextBinaryOperators)) ~ k(" ")
      }
  }
  private def OtherBinaryOperator = {
    grammar("OtherBinaryOperator")
      .single("op") {
        *(k(" ")) ~ named("op" -> enums(OtherBinaryOperators))
      }
  }

  def BinaryOperator = {
    grammar("BinaryOperator")
      .or(TextBinaryOperator, OtherBinaryOperator)
  }

  def BinaryExpression = {
    grammar("BinaryExpression")
      .payload(JavascriptPayloadType.BinaryExpression) {
        k("(") ~ __ ~
          named("a" -> Expressions.BasicExpression) ~
          named("op" -> BinaryOperator) ~ __ ~
          named("b" -> Expressions.BasicExpression) ~ __ ~ k(")")
      }
  }

  // ifs
  private def IfConditionSimple = {
    grammar("IfConditionSimple")
      .single("c") {
        k("(") ~ __ ~ named("c" -> ?(Expressions.BasicExpression)) ~ __ ~ k(")")
      }
  }

  private def IfCondition = {
    grammar("IfCondition")
      .or(IfConditionSimple, BinaryExpression)
  }

  def IfStatement = {
    grammar("IfStatement")
      .payload(JavascriptPayloadType.IfStatement) {
        k("if") ~ __ ~ named("c" -> IfCondition) ~ __ ~ MaybeBrackets
      }
  }

}