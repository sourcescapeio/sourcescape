package models.query.grammar

trait LiteralHelpers extends GrammarHelpers {
  protected def keywordLiteral(words: List[String], payload: PayloadType) = {
    grammar("Keyword").
      payload(payload) {
        named("name" -> enums(words))
      }
  }

  protected def numberLiteral(payload: PayloadType) = {
    grammar("Number").
      payload(payload, "name" -> "text().trim()") {
        pattern("[0-9]+") ~ ?(grouped(k(".") ~ pattern("[0-9]+")))
      }
  }

  protected def stringLiteral(payload: PayloadType) = {
    grammar("String")
      .or(string1(payload), string2(payload))
  }

  private def string1(payload: PayloadType) = {
    grammar("String1")
      .payload(payload, "name" -> "i.join('')") {
        k("\\'") ~ named("i" -> pattern("[^']*")) ~ k("\\'")
      }
  }

  private def string2(payload: PayloadType) = {
    grammar("String2")
      .payload(payload, "name" -> "i.join('')") {
        k("\\\"") ~ named("i" -> pattern("[^\"]*")) ~ k("\\\"")
      }
  }

  // incompletes

  protected def incompleteStringLiteral(payload: PayloadType) = {
    grammar("StringIncomplete")
      .or(stringIncomplete1(payload), stringIncomplete2(payload))
  }

  private def stringIncomplete1(payload: PayloadType) = {
    grammar("StringIncomplete1")
      .payload(payload, "name" -> "i.join('')") {
        k("\\'") ~ named("i" -> pattern("[^']*"))
      }
  }

  private def stringIncomplete2(payload: PayloadType) = {
    grammar("StringIncomplete2")
      .payload(payload, "name" -> "i.join('')") {
        k("\\\"") ~ named("i" -> pattern("[^\"]*"))
      }
  }
}
