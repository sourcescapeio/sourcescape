package models.query.grammar

import Base.__

trait CollectionHelpers {
  self: GrammarHelpers =>

  private val defaultDivider = grouped(__.s ~ k(",") ~ __)
  protected def nonEmptyListGrammar(id: String, attribute: Grammar, divider: Peg = defaultDivider) = {
    grammar(id)
      .single("[head].concat(tail.map((i) => (i[1])))") {
        named("head" -> attribute) ~ named("tail" -> *(divider ~ attribute))
      }
  }

  protected def incompleteListGrammar(
    id:      String,
    pre:     String,
    divider: String = ",")(
    pair:           Grammar,
    pairIncomplete: Grammar) = {

    def PairListPost = {
      grammar(s"${id}PairListPost")
        .single("head.map((i) => (i[0]))") {
          named("head" -> *(grouped(gramToSeg(pair) ~ __ ~ k(divider))))
        }
    }

    grammar(id)
      .single("[...(o1 || []), ...(o2 ? [o2] : [])]") {
        k(pre) ~ __ ~
          named("o1" -> PairListPost) ~ __ ~
          named("o2" -> ?(pairIncomplete))
      }
  }

}