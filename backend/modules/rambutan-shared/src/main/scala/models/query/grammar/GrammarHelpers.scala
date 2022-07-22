package models.query.grammar

import silvousplay.imports._
import scala.language.implicitConversions

trait GrammarHelpers {
  //
  protected def jsArray(items: List[String]) = "[" + items.map("'" + _ + "'").mkString(", ") + "]"

  protected def grammar(id: String) = GrammarBuilder(id)

  //
  protected def pattern(p: String) = PatternSegment(p)
  protected def grouped(p: Peg) = GroupedSegment(p)

  protected def enums(items: List[String]) = MultiSegment(items.map(KeywordSegment.apply))
  protected def multi(pegs: Peg*) = MultiSegment(pegs.toList)

  protected def k(str: String): PegSegment = {
    KeywordSegment(str) // need javascript escape
  }
  protected def named(a: (String, PegSegment)) = a match {
    case (k, seg) => NamedSegment(k, seg)
  }

  implicit def segToPeg(seg: PegSegment): Peg = Peg(seg :: Nil)
  implicit def gramToSeg(grammar: Grammar): PegSegment = GrammarSegment(grammar)

  protected def ?(a: PegSegment) = RepeatedSegment(a, "?")

  protected def *(a: PegSegment) = RepeatedSegment(a, "*")

  protected def ++(a: PegSegment) = RepeatedSegment(a, "+")
}
