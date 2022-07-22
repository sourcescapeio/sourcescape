package models.query.display

import models.query._
import models.query.grammar.ParserType
import silvousplay.imports._
import play.api.libs.json._

case class Highlight(
  id:                 String,
  start_index:        Int,
  end_index:          Int,
  `type`:             HighlightType,
  context:            List[String],
  index:              Option[Int],
  parentId:           Option[String],
  displayType:        Option[String],
  multilineHighlight: Option[String],
  edgeFrom:           Option[String],
  suppressHighlight:  Boolean,
  canGroup:           Boolean,
  replace:            Boolean) {

  def dto = HighlightDTO(
    id,
    `type` match {
      case JavascriptHighlightType.CallArg => s"${id}/${index.getOrElse(0)}" // index should always be non-null
      case t if t.category =?= HighlightCategory.Body => s"${`type`.identifier}/${id}" // multiple types for same body
      case _ => id
    },
    start_index,
    end_index,
    `type`.category,
    `type`.parent,
    `type`.parser,
    `type`.color,
    context,
    index,
    parentId,
    displayType = displayType,
    multilineHighlight = multilineHighlight,
    edgeFrom = edgeFrom,
    suppressHighlight = suppressHighlight,
    canGroup = canGroup,
    replace = replace)
}

case class HighlightDTO(
  id:                 String,
  bodyId:             String,
  start_index:        Int, // snake case because that's what code component expects
  end_index:          Int,
  `type`:             HighlightCategory,
  parent:             Option[EdgePredicate],
  parser:             Option[ParserType],
  color:              Option[HighlightColor],
  context:            List[String],
  index:              Option[Int],
  parentId:           Option[String],
  displayType:        Option[String],
  multilineHighlight: Option[String],
  edgeFrom:           Option[String],
  suppressHighlight:  Boolean,
  canGroup:           Boolean,
  replace:            Boolean)

object HighlightDTO {
  implicit val writes = Json.writes[HighlightDTO]
}
