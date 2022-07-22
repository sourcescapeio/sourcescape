package models

import play.api.libs.json._
import silvousplay.imports._

case class CodeLocation(line: Int, column: Int)

object CodeLocation {

  val empty = CodeLocation(0, 0)

  implicit val format = Json.format[CodeLocation]
}

case class CodeRange(start: CodeLocation, end: CodeLocation, startIndex: Int, endIndex: Int) {

  def span(other: CodeRange) = CodeRange(
    start,
    other.end,
    startIndex,
    other.endIndex)

  def size = endIndex - startIndex

  def displayString = s"L${start.line}:${start.column}-L${end.line}:${end.column}"

}

object CodeRange {
  val empty = CodeRange(CodeLocation.empty, CodeLocation.empty, 0, 0)

  def applyRange(text: String, startIndex: Int, endIndex: Int) = {
    text.drop(startIndex).take(endIndex - startIndex)
  }

  def applyRangeByLine(text: String, start: Int, end: Int, realStart: Int, realEnd: Int) = {
    val chunk = text.split("\n").drop(start - 1).take(end - start + 1)
    val pre = chunk.take(realStart - start)
    val preGap = if (realStart > start) {
      1
    } else {
      0
    }

    (pre.mkString("\n").length + preGap, chunk.mkString("\n"))
  }

  implicit val format = Json.format[CodeRange]
}
