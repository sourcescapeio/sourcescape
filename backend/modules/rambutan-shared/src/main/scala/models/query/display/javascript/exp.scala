package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

case class BinaryDisplay(id: String, name: Option[String], a: Option[DisplayStruct], b: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = List(a, b).flatten

  def chunks(indents: Int, context: List[String]) = {
    val aChunks = withDefined(a) { aa =>
        Option(aa.chunks(indents, context))
      }.getOrElse(DisplayItem.EmptyBody(id, JavascriptHighlightType.BinaryLeft, context) :: Nil)

    val bChunks = withDefined(b) { bb =>
        Option(bb.chunks(indents, context))
      }.getOrElse(DisplayItem.EmptyBody(id, JavascriptHighlightType.BinaryRight, context) :: Nil)

    val multilineHighlight = {
      val l1 = s"(${aChunks.flatMap(_.display).mkString}"
      val l2 = s" ${name.getOrElse("[?]")} "
      val l3 = s"${bChunks.flatMap(_.display).mkString})"
      l1 + l2 + l3
    }

    List(
      DisplayItem(id, "(", HighlightType.WhiteBase, Nil, displayType = Some("binary-op"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      aChunks,
      StaticDisplayItem.Space :: Nil,
      DisplayItem(id, name.getOrElse("[?]"), HighlightType.RedName, Nil, displayType = Some("binary-op"), suppressHighlight = true, canGroup = true) :: Nil,
      StaticDisplayItem.Space :: Nil,
      bChunks,
      DisplayItem(id, ")", HighlightType.WhiteBase, Nil, displayType = Some("binary-op"), suppressHighlight = true) :: Nil,    
    ).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val a = children.getOrElse(JavascriptBuilderEdgeType.BinaryLeft, Nil).headOption.map(_.struct)
    val b = children.getOrElse(JavascriptBuilderEdgeType.BinaryRight, Nil).headOption.map(_.struct)
    this.copy(
      a = a.orElse(this.a),
      b = b.orElse(this.b))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      a = a.map(_.replaceReferences(refMap)),
      b = b.map(_.replaceReferences(refMap))
    )
  }
}

case class UnaryDisplay(id: String, name: Option[String], inner: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = inner.toList  

  def chunks(indents: Int, context: List[String]) = {
    val innerChunks = withDefined(inner) { i =>
      Option(i.chunks(indents, context))
    }.getOrElse(DisplayItem.EmptyItem(id, JavascriptHighlightType.UnaryBody, context) :: Nil)
    val multilineHighlight = {
      s"${name.getOrElse("[?]")}${innerChunks.flatMap(_.display).mkString}"
    }

    List(
      DisplayItem(id, name.getOrElse("[?]"), HighlightType.RedName, Nil, displayType = Some("unary-op"), multilineHighlight = Some(multilineHighlight), canGroup = true) :: Nil,
      innerChunks).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val inner = children.getOrElse(JavascriptBuilderEdgeType.UnaryExpression, Nil).headOption.map(_.struct)
    this.copy(
      inner = inner.orElse(this.inner))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.map(_.replaceReferences(refMap))
    )
  }
}
