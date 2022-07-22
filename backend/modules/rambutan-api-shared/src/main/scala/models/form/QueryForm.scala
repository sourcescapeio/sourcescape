package models

import models.query.{ SrcLogCodeQueryDTO, GroupingType, RelationalKey }
import play.api.libs.json._
import silvousplay.imports._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._

case class QueryForm(
  q: String)

object QueryForm {
  val form = Form(FormsMacro.mapping[QueryForm])
}

case class BuilderQueryForm(
  query:    SrcLogCodeQueryDTO,
  queryKey: String,
  // targeting
  indexIds:   Option[List[Int]],
  fileFilter: Option[String],
  // scroll
  offset: Option[Int],
  limit:  Option[Int])

object BuilderQueryForm {
  implicit val format = Json.format[BuilderQueryForm]
}

case class GroupedQueryForm(
  query:    SrcLogCodeQueryDTO,
  grip:     String,
  grouping: GroupingType,
  selected: List[String],
  repos:    Option[List[String]])

object GroupedQueryForm {
  implicit val format = Json.format[GroupedQueryForm]
}

case class SnapshotQueryForm(
  query:    SrcLogCodeQueryDTO,
  selected: List[String],
  named:    List[String],
  // targeting
  indexIds:   Option[List[Int]],
  fileFilter: Option[String],
  limit:      Option[Int])

object SnapshotQueryForm {
  implicit val reads = Json.reads[SnapshotQueryForm]
}
