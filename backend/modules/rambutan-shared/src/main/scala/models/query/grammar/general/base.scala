package models.query.grammar

object Base extends GrammarHelpers {

  // def listGrammar(id: String, attribute: Grammar, divider: Peg = defaultDivider) = {
  //   grammar(id)
  //     .single("items || []") {
  //       named("items" -> ?(nonEmptyListGrammar(id, attribute, divider)))
  //     }
  // }

  def __ = {
    grammar("_")
      .onlyPattern {
        pattern("[ \\t\\n\\r]*")
      }
  }

  def MaybeBrackets = ?(k("{") ~ __ ~ ?(k("}")))

  // universal
  def RefIdentifier = {
    grammar("RefIdentifier")
      .single("[a[0], ...a[1]].join('').trim()") {
        named("a" -> pattern("([A-Z][A-Za-z]*)"))
      }
  }

  def RefExpression = {
    grammar("RefExpression")
      .payload(BasePayloadType.Ref, "name" -> "ref") {
        k("ref[") ~ named("ref" -> RefIdentifier) ~ k("]")
      }
  }

  def RefIncomplete = {
    grammar("RefIncomplete")
      .payload(BasePayloadType.Ref, "name" -> "ref") {
        k("ref[") ~ named("ref" -> ?(RefIdentifier))
      }
  }
}
