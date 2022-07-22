package models.query.display.javascript

import models.query.display._
import models.query._
import silvousplay.imports._
import play.api.libs.json._

case class RequireDisplay(id: String, name: Option[String], aliasIn: Option[String]) extends DisplayStruct with NoChildrenDisplayStruct {

  private val nameAlias = name.flatMap(_.split("/").lastOption)

  override val alias = aliasIn.orElse(nameAlias)

  override val traverse = Some(HighlightType.Traverse)

  private def nameDisplay = {
    withDefined(name) { n =>
      Option {
        DisplayItem.Name(id, s"'${n}'", "require")
      }
    }.getOrElse(DisplayItem.EmptyName(id, "require"))
  }

  def chunks(indents: Int, context: List[String]) = {
    val multilineHighlight = s"require(${nameDisplay.display})"

    List(
    DisplayItem(id, s"require", HighlightType.BlueBase, Nil, displayType = Some("require"), multilineHighlight = Some(multilineHighlight), canGroup = true),
    StaticDisplayItem.OpenParens,
    nameDisplay,
    StaticDisplayItem.CloseParens)
  }
}

case class IdentifierDisplay(id: String, name: Option[String]) extends DisplayStruct with NoChildrenDisplayStruct{

  override val traverse = Some(HighlightType.Traverse)

  def chunks(indents: Int, context: List[String]) = {
    DisplayItem(id, name.getOrElse("_____"), HighlightType.WhiteName, Nil, displayType = Some("identifier"), canGroup = true) :: Nil
  }
}


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