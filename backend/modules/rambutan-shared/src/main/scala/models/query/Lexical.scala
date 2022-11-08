package models.query

import fastparse._
import NoWhitespace._

object Lexical {
  private def quotedC(c: Char) = c != '"' && c != '[' && c != ']' && c != '=' && c != ',' && c != '(' && c != ')' && c != '{' && c != '}'
  def quotedChars[_: P] = P(CharsWhile(quotedC)).rep.!

  def keywordChars[_: P] = P(CharIn("0-9a-zA-Z_\\-:").rep(1).!)

  def numChars[_: P] = P(CharIn("0-9").rep(1).!)
}
