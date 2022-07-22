package models.query.display

import models.query._
import silvousplay.imports._
import play.api.libs.json._

sealed trait DisplayChunk {
  def display: String

  def apply(startIdx: Int): (Int, List[HighlightDTO])

}

object DisplayChunk {
  val TabSize = 4
}

case class StaticDisplayItem(display: String, color: String) extends DisplayChunk {
  def apply(startIdx: Int): (Int, List[HighlightDTO]) = {
    (startIdx + display.length, Nil)
  }
}

case class DisplayItem(
  id:                 String,
  display:            String,
  `type`:             HighlightType,
  context:            List[String],
  displayType:        Option[String],
  index:              Option[Int]    = None,
  parentId:           Option[String] = None,
  multilineHighlight: Option[String] = None,
  edgeFrom:           Option[String] = None,
  suppressHighlight:  Boolean        = false,
  canGroup:           Boolean        = false,
  replace:            Boolean        = false) extends DisplayChunk {
  def apply(startIdx: Int): (Int, List[HighlightDTO]) = {

    val endIdx = startIdx + display.length
    val highlight = Highlight(
      id,
      start_index = startIdx,
      end_index = endIdx,
      `type` = `type`,
      context = context,
      index = index,
      parentId = parentId,
      displayType = displayType,
      multilineHighlight = multilineHighlight,
      edgeFrom = edgeFrom,
      suppressHighlight = suppressHighlight,
      canGroup = canGroup,
      replace = replace)

    (endIdx, highlight.dto :: Nil)
  }
}

object DisplayItem {
  def EmptyName(id: String, displayType: String) = Name(id, "___", displayType)

  def Name(id: String, name: String, displayType: String) = {
    DisplayItem(id, name, HighlightType.Name, Nil, displayType = Some(displayType), suppressHighlight = true)
  }

  def EmptyBody(id: String, `type`: HighlightType, context: List[String]) = {
    // body menu is ignored on frontend
    DisplayItem(id, "...", `type`, context, displayType = None)
  }

  def EmptyItem(id: String, `type`: HighlightType, context: List[String]) = {
    // body menu is ignored on frontend
    DisplayItem(id, "___", `type`, context, displayType = None)
  }
}

object StaticDisplayItem {
  val OpenParens = StaticDisplayItem("(", "")
  val CloseParens = StaticDisplayItem(")", "")
  val OpenBracket = StaticDisplayItem("{", "")
  val CloseBracket = StaticDisplayItem("}", "")
  val OpenCaret = StaticDisplayItem("<", "")
  val CloseCaret = StaticDisplayItem(">", "")
  val Comma = StaticDisplayItem(", ", "")
  val Space = StaticDisplayItem(" ", "")
  val Equals = StaticDisplayItem("=", "")
  val Backquote = StaticDisplayItem("`", "")

  def NewLine(n: Int, indents: Int) = StaticDisplayItem(("\n" + (" " * indents)) * n, "")
}
