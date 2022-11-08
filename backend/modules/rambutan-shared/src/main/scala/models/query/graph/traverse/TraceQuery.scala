package models.query

import fastparse._
import MultiLineWhitespace._

sealed case class FromRoot(name: String, leftJoin: Boolean)

case class TraceQuery(from: FromRoot, traverses: List[Traverse]) {

  def assignLeftJoin(leftJoin: Boolean) = {
    traverses match {
      case Nil => this.copy(from = from.copy(leftJoin = leftJoin))
      case _   => this
    }
  }

  def fromName = from.name
}

object TraceQuery {

  private def leftJoin[_: P] = P("left_join[" ~ Lexical.keywordChars ~ "]").map(k => FromRoot(k, true))
  private def innerJoin[_: P] = P("join[" ~ Lexical.keywordChars ~ "]").map(k => FromRoot(k, false))
  private def join[_: P] = P(leftJoin | innerJoin)

  def query[_: P] = P(join ~ ("." ~ GraphQuery.Traverse.traverses).rep(0)) map {
    case (join, traverses) => TraceQuery(join, traverses.toList)
  }
}
