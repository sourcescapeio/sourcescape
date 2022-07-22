package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

case class JSXAttributeDisplay(id: String, name: Option[String], value: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = value.toList

  override val multiline = value.exists(_.multiline)

  def chunks(indents: Int, context: List[String]) = {
    val valueChunks = withDefined(value) { v =>
      val nl = withFlag(v.multiline) {
        StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil
      }
      val indentAddition = withFlag(v.multiline) {
        Option(DisplayChunk.TabSize)
      }.getOrElse(0)

      Option {
        nl ++ v.chunks(indents + indentAddition, context)
      }
    }.getOrElse {
      DisplayItem.EmptyBody(id, JavascriptHighlightType.JSXAttributeValue, Nil) :: Nil
    }

    val multilineHighlight = {
      s"${name.getOrElse("____")}={${valueChunks.flatMap(_.display).mkString}}"
    }

    List(
      DisplayItem(id, " ", HighlightType.Empty, Nil, displayType = Some("jsx-attr"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      withDefined(name) { n =>
        Option(DisplayItem(id, n, HighlightType.Name, Nil, displayType = Some("jsx-attr")) :: Nil)
      }.getOrElse {
        DisplayItem(id, "____", HighlightType.Name, Nil, displayType = Some("jsx-attr")) :: Nil
      }.map(_.copy(suppressHighlight = true, canGroup = true)),
      StaticDisplayItem.Equals :: Nil,
      StaticDisplayItem.OpenBracket :: Nil,
      //
      valueChunks,
      //
      withFlag(multiline) {
        StaticDisplayItem.NewLine(1, indents) :: Nil
      },
      StaticDisplayItem.CloseBracket :: Nil).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val value = children.getOrElse(JavascriptBuilderEdgeType.JSXAttributeValue, Nil).headOption.map(_.struct)
    this.copy(value = value.orElse(this.value))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      value = value.map(_.replaceReferences(refMap)))
  }
}

case class JSXElementDisplay(id: String, name: Option[String], tag: Option[DisplayStruct], attributes: List[DisplayStruct], jsxChild: List[DisplayStruct]) extends DisplayStruct with BodyStruct {
  val body = jsxChild

  def children: List[DisplayStruct] = jsxChild

  override val multiline = true

  def chunks(indents: Int, context: List[String]) = {

    val nameChunks = withDefined(tag) { t =>
      Option {
        t.chunks(indents, Nil)
      }
    }.getOrElse(DisplayItem(id, "___", JavascriptHighlightType.JSXTag, context, displayType = Some("jsx")).copy(suppressHighlight = true) :: Nil)

    val nameChunksString = nameChunks.map(_.display).mkString

    val attributesMultiline = attributes.exists(_.multiline)

    val attributeSeparator = if (attributesMultiline) {
      StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize)
    } else {
      StaticDisplayItem.Space
    }
    val attributeChunks: List[DisplayChunk] = attributes.flatMap { attr =>
      val indentAddition = withFlag(attributesMultiline) {
        Option(DisplayChunk.TabSize)
      }.getOrElse(0)
      attributeSeparator :: attr.chunks(indents + indentAddition, context)
    }
    val childChunks = ifNonEmpty(jsxChild) {
      Option(jsxChild.flatMap(_.chunks(indents + DisplayChunk.TabSize, context)))
    }.getOrElse(DisplayItem.EmptyBody(id, JavascriptHighlightType.JSXChild, Nil) :: Nil)

    val multilineHighlight = {
      val l1 = s"<${nameChunksString}${attributeChunks.flatMap(_.display).mkString}${attributeSeparator.display}...${attributeSeparator.display}>"
      val l2 = StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize).display
      val l3 = s"${childChunks.flatMap(_.display).mkString}\n</${nameChunksString}>"
      l1 + l2 + l3
    }

    List(
      DisplayItem(id, "<", HighlightType.WhiteBase, Nil, displayType = Some("jsx"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      nameChunks,
      attributeChunks,
      attributeSeparator :: Nil,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.JSXAttribute, context) :: Nil,
      withFlag(attributesMultiline) {
        StaticDisplayItem.NewLine(1, indents) :: Nil
      },
      DisplayItem(id, ">", HighlightType.WhiteBase, Nil, displayType = Some("jsx")).copy(
        suppressHighlight = true) :: Nil,
      StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil,
      childChunks,
      StaticDisplayItem.NewLine(1, indents) :: Nil,
      DisplayItem(id, "</", HighlightType.WhiteBase, Nil, displayType = Some("jsx")).copy(suppressHighlight = true) :: Nil,
      DisplayItem(id, nameChunksString, HighlightType.WhiteBase, Nil, displayType = Some("jsx")).copy(
        suppressHighlight = true) :: Nil,
      DisplayItem(id, ">", HighlightType.WhiteBase, Nil, displayType = Some("jsx")).copy(suppressHighlight = true) :: Nil).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val tag = children.getOrElse(JavascriptBuilderEdgeType.JSXTag, Nil).headOption.map(_.struct)
    val attrs = children.getOrElse(JavascriptBuilderEdgeType.JSXAttribute, Nil).map(_.struct)
    // we current restrict this as only one on the front-end
    // backend supports multiple, tho
    val jsxChild = children.getOrElse(JavascriptBuilderEdgeType.JSXChild, Nil).map(_.struct)
    this.copy(
      tag = tag.orElse(this.tag),
      attributes = this.attributes ++ attrs,
      jsxChild = this.jsxChild ++ jsxChild)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      tag = tag.map(_.replaceReferences(refMap)),
      attributes = attributes.map(_.replaceReferences(refMap)),
      jsxChild = jsxChild.map(_.replaceReferences(refMap)))
  }
}
