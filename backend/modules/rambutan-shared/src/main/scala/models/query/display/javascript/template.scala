package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._

case class TemplateExpressionDisplay(id: String, index: Option[Int], inner: Option[DisplayStruct]) extends DisplayStruct with BodyStruct {
  val body = inner.toList

  def chunks(indents: Int, context: List[String]): List[DisplayChunk] = {

    val baseChunks = inner.toList.flatMap(_.chunks(indents, context))

    val multilineHighlight = "${" + baseChunks.flatMap(_.display).mkString + "}"

    val argIndex = DisplayItem(
      id,
      index.map(_.toString).getOrElse("*"),
      HighlightType.IndexOnly,
      Nil,
      displayType = None,
      index = index,
      parentId = None)

    List(
      DisplayItem(id, " ", HighlightType.Empty, Nil, displayType = Some("template"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      DisplayItem(id, "${", HighlightType.WhiteBase, Nil, displayType = Some("template"), suppressHighlight = true) :: Nil,
      baseChunks,
      DisplayItem(id, "}", HighlightType.WhiteBase, Nil, displayType = Some("template"), suppressHighlight = true) :: Nil,
      argIndex :: Nil).flatten
  }

  def children = inner.toList

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val child = children.getOrElse(JavascriptBuilderEdgeType.TemplateContains, Nil).headOption
    this.copy(inner = child.map(_.struct))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.map(_.replaceReferences(refMap)))
  }
}

case class TemplateLiteralDisplay(id: String, components: List[DisplayStruct]) extends DisplayStruct with NoBodyStruct {
  def chunks(indents: Int, context: List[String]): List[DisplayChunk] = {

    val baseChunks = components.flatMap {
      case c: LiteralStringDisplay => c.chunks(indents, context).drop(1).dropRight(1)
      case c                       => c.chunks(indents, context)
    }

    val multilineHighlight = "`" + baseChunks.flatMap(_.display).mkString + "`"

    List(
      DisplayItem(id, " ", HighlightType.Empty, Nil, displayType = Some("template"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      DisplayItem(id, "`", HighlightType.WhiteBase, Nil, displayType = Some("template"), suppressHighlight = true) :: Nil,
      baseChunks,
      DisplayItem(id, "`", HighlightType.WhiteBase, Nil, displayType = Some("template"), suppressHighlight = true) :: Nil).flatten
  }

  def children = components

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {

    val components = children.getOrElse(JavascriptBuilderEdgeType.TemplateComponent, Nil).map { c =>
      c.struct match {
        case s: TemplateExpressionDisplay => s.copy(index = c.index)
        case o                            => o
      }
    }

    this.copy(
      components = this.components ++ components)
  }

  // for components
  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      components = components.map(_.replaceReferences(refMap)))
  }
}