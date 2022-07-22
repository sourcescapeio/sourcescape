package models.query.display.ruby

import models.query._
import models.query.display._
import silvousplay.imports._


case class LiteralDisplay(id: String, name: Option[String]) extends DisplayStruct with NoChildrenDisplayStruct {

  def chunks(indents: Int, context: List[String]) = List(
    // Only allow grouping when is empty
    withDefined(name) { n =>
      Option(DisplayItem(id, n, HighlightType.PurpleBase, Nil, displayType = Some("literal")))
    }.getOrElse(DisplayItem(id, "____", HighlightType.PurpleBase, Nil, displayType = Some("literal"), canGroup = true)),
  )
}

case class LiteralStringDisplay(id: String, name: Option[String]) extends DisplayStruct with NoChildrenDisplayStruct {

  def chunks(indents: Int, context: List[String]) = List(
    StaticDisplayItem("\"", ""),
    withDefined(name) { n =>
      Option(DisplayItem(id, n, HighlightType.YellowName, Nil, displayType = Some("string"), canGroup = true))
    }.getOrElse(DisplayItem(id, "____", HighlightType.YellowName, Nil, displayType = Some("string"), canGroup = true)),
    StaticDisplayItem("\"", "")
  )
}
