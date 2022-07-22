package models.query.grammar.javascript

import models.query.grammar._

object IdentIncomplete extends GrammarHelpers {

  def DepIncomplete = {
    grammar("DepIncomplete")
      .payload(JavascriptPayloadType.Dep, "name" -> "dep && dep.name") {
        k("dep[") ~ named("dep" -> ?(Ident.IdentifierExpression))
      }
  }
}

object Ident extends GrammarHelpers {

  def DefinitionName = {
    grammar("DefinitionName")
      .single("name") {
        k(" ") ~ named("name" -> IdentifierExpression)
      }
  }

  private def AnyIdentifier = {
    grammar("AnyIdentifier")
      .payload(JavascriptPayloadType.Identifier) {
        k("?")
      }
  }

  private def RawIdentifier = {
    grammar("RawIdentifier")
      .single("[a[0], ...a[1]].join('').trim()") {
        named("a" -> pattern("([$_A-Za-z_][$_A-Za-z_0-9]*)"))
      }
  }

  val ReservedWords = List(
    "new", "extends", "class", "function",
    "return", "yield", "throw", "await", "if",
    "require")
  val BannedWords = ReservedWords ++ Cond.PrefixUnaryOperators ++ Cond.TextBinaryOperators ++ Literal.Keywords

  def Identifier = {
    grammar("Identifier")
      .payload(JavascriptPayloadType.Identifier) {
        named("name" -> RawIdentifier)
      }
      .constraint {
        s"""
const BannedWords = ${jsArray(BannedWords)};

return !BannedWords.includes(name);
        """
      }
  }

  private def FreeIdentifier = {
    grammar("FreeIdentifier")
      .payload(JavascriptPayloadType.Identifier) {
        named("name" -> RawIdentifier)
      }
  }

  def FreeIdentifierExpression = {
    grammar("RawIdentifierExpression")
      .or(
        AnyIdentifier,
        FreeIdentifier)
  }

  def IdentifierExpression = {
    grammar("IdentifierExpression")
      .or(
        AnyIdentifier,
        Identifier)
  }

  def DepExpression = {
    grammar("DepExpression")
      .payload(JavascriptPayloadType.Dep, "name" -> "dep") {
        k("dep[") ~ named("dep" -> ?(Base.RefIdentifier)) ~ k("]")
      }
  }

  def RefOrIdent = {
    grammar("RefOrIdent")
      .or(
        Base.RefExpression,
        IdentifierExpression)
  }

}
