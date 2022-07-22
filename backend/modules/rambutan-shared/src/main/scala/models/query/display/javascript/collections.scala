package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

case class ArrayDisplay(id: String, elements: List[DisplayStructContainer]) extends DisplayStruct with NoBodyStruct {
  override val multiline = elements.exists(_.struct.multiline)

  def children: List[DisplayStruct] = elements.map(_.struct)

  def chunks(indents: Int, context: List[String]) = {
    val additionalIndent = withFlag(multiline)(Option(DisplayChunk.TabSize)).getOrElse(0)

    val (maxId, calculatedElements) = CallArg.calculateIndexes(id, elements, JavascriptHighlightType.ArrayMember)
    val elementChunks = calculatedElements.flatMap { i =>
      List(
        withFlag(multiline) {
          StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil
        },
        i.chunks(indents + DisplayChunk.TabSize, context),
        StaticDisplayItem.Comma :: Nil).flatten
    }

    val multilineHighlight = {
      val l1 = s"[${elementChunks.flatMap(_.display).mkString}"
      val l2 = if (multiline) {
        s"\n${" " * DisplayChunk.TabSize}...\n]"
      } else {
        "...]"
      }
      l1 + l2
    }

    List(
      DisplayItem(id, s"[", HighlightType.WhiteBase, Nil, displayType = Some("array"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      elementChunks,
      withFlag(multiline) {
        StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil
      },
      EmptyCallArgDisplay(id, isNew = true, index = maxId.map(_ + 1).getOrElse(1), JavascriptHighlightType.ArrayMember).chunks(indents, context),
      withFlag(multiline) {
        StaticDisplayItem.NewLine(1, indents) :: Nil
      },
      DisplayItem(id, s"]", HighlightType.WhiteBase, Nil, displayType = Some("array"), suppressHighlight = true) :: Nil).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    this.copy(
      elements = this.elements ++ children.getOrElse(JavascriptBuilderEdgeType.ArrayMember, Nil))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      elements = elements.map { e =>
        e.copy(struct = e.struct.replaceReferences(refMap))
      })
  }
}

case class ObjectPropertyDisplay(id: String, name: Option[String], inner: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = inner.toList

  override val multiline = inner.exists(_.multiline)

  def chunks(indents: Int, context: List[String]) = {
    val additionalIndent = withFlag(multiline)(Option(DisplayChunk.TabSize)).getOrElse(0)

    val innerChunks = inner match {
      case Some(i) => i.chunks(indents + additionalIndent, context)
      case None    => DisplayItem.EmptyItem(id, JavascriptHighlightType.ObjectPropertyValue, context) :: Nil
    }
    val maybeNewLine = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil)

    val multilineHighlight = {
      s"${name.getOrElse("___")} : ${maybeNewLine.flatMap(_.display).mkString}${innerChunks.flatMap(_.display).mkString}"
    }

    List(
      DisplayItem(id, name.getOrElse("___"), HighlightType.YellowName, Nil, displayType = Some("object-property"), multilineHighlight = Some(multilineHighlight), canGroup = true) :: Nil,
      StaticDisplayItem(" : ", "") :: Nil,
      maybeNewLine,
      innerChunks).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val inner = children.getOrElse(JavascriptBuilderEdgeType.ObjectPropertyValue, Nil).headOption.map(_.struct)
    this.copy(inner = inner.orElse(this.inner))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.map(_.replaceReferences(refMap)))
  }
}

case class ObjectDisplay(id: String, properties: List[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = properties

  override val multiline = true

  def chunks(indents: Int, context: List[String]) = {
    val propertiesChunks = properties.flatMap { p =>
      List(
        StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil,
        p.chunks(indents + DisplayChunk.TabSize, context),
        StaticDisplayItem.Comma :: Nil).flatten
    }

    val multilineHighlight = s"{${propertiesChunks.flatMap(_.display).mkString}\n${" " * DisplayChunk.TabSize}...\n}"

    List(
      DisplayItem(id, s"{", HighlightType.WhiteBase, Nil, displayType = Some("object"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      propertiesChunks,
      StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.ObjectProperty, context) :: Nil,
      StaticDisplayItem.NewLine(1, indents) :: Nil,
      DisplayItem(id, s"}", HighlightType.WhiteBase, Nil, displayType = Some("object"), suppressHighlight = true) :: Nil).flatten
  }
  //   case Nil => DisplayItem(id, "{ ... }", HighlightType.Object, argIndex = None, booleanState = None) :: Nil
  //   case props => {
  //     List(
  //       DisplayItem(id, "{", HighlightType.Object, argIndex = None, booleanState = None) :: Nil,
  //       properties.flatMap(_.chunks(indents + DisplayChunk.TabSize)),
  //
  //       StaticDisplayItem.CloseBracket :: Nil).flatten
  //   }
  // }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val props = children.getOrElse(JavascriptBuilderEdgeType.ObjectProperty, Nil).map(_.struct)
    this.copy(properties = this.properties ++ props)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      properties = properties.map(_.replaceReferences(refMap)))
  }
}