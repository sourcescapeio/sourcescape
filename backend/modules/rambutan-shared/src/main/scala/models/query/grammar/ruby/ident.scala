package models.query.grammar.ruby

import models.query.grammar._
import Base._

object Ident extends GrammarHelpers {

  //   val ReservedWords = List(
  //     "case",
  //     "new", "extends", "class", "function",
  //     "return", "yield", "throw", "await", "if",
  //     "require")
  val BannedWords = Nil
  //   // ++ Cond.PrefixUnaryOperators ++ Cond.TextBinaryOperators ++ Literal.Keywords

  private def AnyIdentifierRaw = {
    grammar("AnyIdentifierRaw")
      .single("'?'") {
        k("?")
      }
  }

  private def RawIdentifier = {
    grammar("RawIdentifier")
      .single("[a[0], ...a[1]].join('').trim()") {
        named("a" -> pattern("([$_A-Za-z_][$_A-Za-z_0-9]*)"))
      }
  }

  private def Identifier = {
    grammar("Identifier")
      .payload(RubyPayloadType.Identifier) {
        named("name" -> RawIdentifier)
      }
      .constraint {
        s"""
  const BannedWords = ${jsArray(BannedWords)};

  return !BannedWords.includes(name);
          """
      }
  }

  def AnyIdentifier = {
    grammar("AnyIdentifier")
      .payload(RubyPayloadType.Identifier) {
        k("?")
      }
  }

  // This is used differently from Javascript
  // This is like a multi-search on anything named X
  def IdentifierExpression = {
    grammar("IdentifierExpression")
      .or(
        AnyIdentifier,
        Identifier)
  }

  /**
   * Const
   */
  private def ConstIdentifierRaw = {
    grammar("ConstIdentifierRaw")
      .single("[a[0], ...a[1]].join('').trim()") {
        named("a" -> pattern("([A-Z][$_A-Za-z_0-9]*)"))
      }
  }

  private def SendIdentifierRaw = {
    grammar("SendIdentifierRaw")
      .single("a + b[1]") {
        named("a" -> RawIdentifier) ~ named("b" -> pattern("([ ]*[=])"))
      }
  }

  def ConstIdentifier = {
    grammar("ConstIdentifier")
      .or(
        // NothingIdentifier,
        AnyIdentifierRaw,
        ConstIdentifierRaw)
  }

  // Sends
  def SendIdentifier = {
    grammar("SendIdentifier")
      .or(
        // NothingIdentifier,
        AnyIdentifierRaw,
        SendIdentifierRaw,
        RawIdentifier,
      )
  }
}
