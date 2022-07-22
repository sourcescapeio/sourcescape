package models.query.display

import models.query._
import models.query.display._

// also used by ClassMethod
case class FunctionArgDisplay(id: String, parentId: Option[String], name: String, index: Option[Int]) extends NoChildrenDisplayStruct {
  override val alias = Some(name)

  def chunks(indents: Int, context: List[String]) = {
    List(
      DisplayItem(id, name, HighlightType.Arg, Nil, displayType = Some("function-arg"), index = index, parentId = parentId), // index and parentId for dragging
      DisplayItem(id, index.map(_.toString).getOrElse("*"), HighlightType.IndexOnly, Nil, displayType = None, index = index))
  }
}
