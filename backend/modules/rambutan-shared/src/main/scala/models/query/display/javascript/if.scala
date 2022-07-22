package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

case class IfDisplay(id: String, condition: Option[DisplayStruct], body: List[DisplayStruct]) extends DisplayStruct with BodyStruct{

  def children: List[DisplayStruct] = body  

  def chunks(indents: Int, context: List[String]) = {
    val conditionChunks = condition match {
      case Some(cond) => cond.chunks(indents, context)
      case None       => DisplayItem.EmptyBody(id, JavascriptHighlightType.IfCondition, context) :: Nil
    }

    val bodyChunks = FunctionBody.applyChunks(body, indents, context, spacing = 2)

    val multilineHighlight = {
      Option(s"if(${conditionChunks.flatMap(_.display).mkString}){\n${bodyChunks.flatMap(_.display).mkString}\n}")
    }

    List(
      DisplayItem(id, "if", HighlightType.RedBase, Nil, displayType = Some("if"), multilineHighlight = multilineHighlight) :: Nil,
      StaticDisplayItem.OpenParens :: Nil,
      conditionChunks,
      StaticDisplayItem.CloseParens :: Nil,
      StaticDisplayItem.OpenBracket :: Nil,
      StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil,
      bodyChunks,
      ifNonEmpty(bodyChunks) {
        StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize) :: Nil
      },
      DisplayItem.EmptyBody(id, JavascriptHighlightType.IfBody, context) :: Nil,
      StaticDisplayItem.NewLine(2, indents) :: Nil,
      StaticDisplayItem.CloseBracket :: Nil).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val condition = children.getOrElse(JavascriptBuilderEdgeType.IfCondition, Nil).map(_.struct).headOption
    val body = children.getOrElse(JavascriptBuilderEdgeType.IfBody, Nil).map(_.struct)
    this.copy(
      condition = condition.orElse(this.condition),
      body = this.body ++ body,
    )
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      condition = condition.map(_.replaceReferences(refMap)),
      body = FunctionBody.calculateBodyReplace(body, refMap))
  }
}
