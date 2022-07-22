package models.query.grammar.scalap

import models.query.grammar._
import Base._

object Expressions extends GrammarHelpers {

  val ReservedWords = List(
    "case",
    "new", "extends", "class", "function",
    "return", "yield", "throw", "await", "if",
    "require")
  val BannedWords = ReservedWords
  // ++ Cond.PrefixUnaryOperators ++ Cond.TextBinaryOperators ++ Literal.Keywords

  private def RawIdentifier = {
    grammar("RawIdentifier")
      .single("[a[0], ...a[1]].join('').trim()") {
        named("a" -> pattern("([$_A-Za-z_][$_A-Za-z_0-9]*)"))
      }
  }

  def Identifier = {
    grammar("Identifier")
      .payload(ScalaPayloadType.Identifier) {
        named("name" -> RawIdentifier)
      }
      .constraint {
        s"""
const BannedWords = ${jsArray(BannedWords)};

return !BannedWords.includes(name);
        """
      }
  }

  // def IdentifierExpression = {
  //   grammar("IdentifierExpression")
  //     .or(
  //       AnyIdentifier,
  //       Identifier)
  // }

  def Trait = {
    grammar("Trait")
      .payload(ScalaPayloadType.Trait) {
        k("trait ") ~ __ ~ named("name" -> Identifier)
      }
  }
}