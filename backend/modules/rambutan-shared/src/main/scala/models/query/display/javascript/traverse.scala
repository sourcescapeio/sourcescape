package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

// sealed trait 

case class MemberDisplay(id: String, base: Option[DisplayStruct], name: Option[String], aliasIn: Option[String]) extends TraverseDisplayStruct with NoBodyStruct {

  def copyBase(base: DisplayStruct) = this.copy(base = Some(base))

  def children: List[DisplayStruct] = base.toList

  override def filterEmpty = {
    this.copy(base = base.flatMap(_.filterEmpty.headOption)) :: Nil
  }

  override val alias = aliasIn

  override val traverse = Some(HighlightType.Traverse)

  def chunks(indents: Int, context: List[String]) = List(
    withDefined(base) { b =>
      {
        Option(b.chunks(indents, Nil))
      }
    }.getOrElse(DisplayItem.EmptyItem(id, JavascriptHighlightType.MemberStart, context).copy(edgeFrom=Some(id)) :: Nil), // Need change
    StaticDisplayItem(".", "") :: Nil,
    withDefined(name) { n =>
      Option(DisplayItem(id, n, HighlightType.WhiteName, Nil, displayType = Some("member")).copy(
        canGroup = true,
        suppressHighlight = false
      ) :: Nil)
    }.getOrElse(DisplayItem.EmptyName(id, "member").copy(
      `type` = HighlightType.WhiteName,
      canGroup = true,
      suppressHighlight = false
    ) :: Nil),
  ).flatten

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    DisplayContainer.applyContainer(this, children)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      base = base.map(_.replaceReferences(refMap))
    )
  }
}

// TODO: how to display call selected?
case class CallDisplay(id: String, base: Option[DisplayStruct], args: List[DisplayStructContainer], aliasIn: Option[String]) extends TraverseDisplayStruct with NoBodyStruct{

  def copyBase(base: DisplayStruct) = this.copy(base = Some(base))

  def children: List[DisplayStruct] = base.toList ++ args.map(_.struct)

  override def filterEmpty = {
    this.copy(
      base = base.flatMap(_.filterEmpty.headOption),
      args = args.flatMap(_.filterEmpty)
    ) :: Nil
  }

  override val alias = aliasIn

  override val multiline = args.exists(_.struct.multiline)

  override val traverse = Some(HighlightType.Traverse)

  def chunks(indents: Int, context: List[String]) = {
    val baseChunks = withDefined(base) { b =>
      {
        Option(b.chunks(indents, Nil))
      }
    }.getOrElse(DisplayItem.EmptyItem(id, JavascriptHighlightType.CallStart, context).copy(edgeFrom=Some(id)) :: Nil)

    val (maxId, calculatedArgs) = CallArg.calculateIndexes(id, args, JavascriptHighlightType.CallArg)
    val argsIndents = if(multiline) {
      indents + DisplayChunk.TabSize
    } else {
      indents
    }

    val argsChunks: List[DisplayChunk] = calculatedArgs.flatMap { arg => 
      if (multiline) {
        arg.chunks(argsIndents, context) ++ List(StaticDisplayItem.Comma, StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize))
      } else {
        arg.chunks(indents, context) :+ StaticDisplayItem.Comma
      }
    } ++ EmptyCallArgDisplay(id, isNew = true, index = maxId.map(_ + 1).getOrElse(1), JavascriptHighlightType.CallArg).chunks(argsIndents, context)

    val maybeNewLine = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil)
    val maybeNewLine2 = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents) :: Nil)    

    val multilineHighlight = {
      val l1 = s"${baseChunks.flatMap(_.display).mkString}"
      val l2 = s"(${maybeNewLine.flatMap(_.display).mkString}"
      val l3 = s"${argsChunks.flatMap(_.display).mkString}...${maybeNewLine2.flatMap(_.display).mkString})"
      l1 + l2 + l3
    }

    List(
      DisplayItem(id, " ", HighlightType.Empty, Nil, displayType = None, multilineHighlight = Some(multilineHighlight)) :: Nil,
      baseChunks,
      DisplayItem(id, "(", HighlightType.WhiteBase, Nil, displayType = Some("call"), suppressHighlight = true) :: Nil,
      // args
      maybeNewLine,
      argsChunks,
      maybeNewLine2,
      // ...
      DisplayItem(id, ")", HighlightType.WhiteBase, Nil, displayType = Some("call"), suppressHighlight = true) :: Nil
    ).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val args = children.getOrElse(JavascriptBuilderEdgeType.CallArg, Nil)

    DisplayContainer.applyContainer(
      this.copy(args = this.args ++ args),
      children - JavascriptBuilderEdgeType.CallArg
    )
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      base = base.map(_.replaceReferences(refMap)),
      args = args.map { container => 
        container.copy(
          struct = container.struct.replaceReferences(refMap))
      }
    )
  }
}
