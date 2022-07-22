package models.query.display.ruby

import models.query._
import models.query.display._
import silvousplay.imports._
import models.index.ruby.RubyEdgeType

case class CNullDisplay(id: String) extends NoChildrenDisplayStruct {
  def chunks(indents: Int, context: List[String]) = {
    Nil
  }
}

case class ConstDisplay(id: String, base: Option[DisplayStruct], name: Option[String]) extends TraverseDisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = base.toList

  def copyBase(base: DisplayStruct) = this.copy(base = Some(base))

  override val traverse = Some(RubyHighlightType.ConstTraverse)

  def chunks(indents: Int, context: List[String]) = List(
    withDefined(base) {
      case n: CNullDisplay => Nil
      case b => b.chunks(indents, Nil) :+ StaticDisplayItem("::", "")
    },
    withDefined(name) { n =>
      Option(DisplayItem(id, n, HighlightType.WhiteName, Nil, displayType = Some("const")).copy(
        canGroup = true,
        suppressHighlight = false
      ) :: Nil)
    }.getOrElse(DisplayItem.EmptyName(id, "const").copy(
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
      base = base.map(_.replaceReferences(refMap)))
  }

}

case class SNullDisplay(id: String) extends NoChildrenDisplayStruct {
  def chunks(indents: Int, context: List[String]) = {
    Nil
  }
}

case class SendDisplay(id: String, base: Option[DisplayStruct], name: Option[String], args: List[DisplayStructContainer]) extends TraverseDisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = base.toList

  override val multiline = args.exists(_.struct.multiline)

  def copyBase(base: DisplayStruct) = this.copy(base = Some(base))

  override val traverse = args match {
    case Nil => Some(RubyHighlightType.TraverseWithArg)
    case _ => Some(HighlightType.Traverse)
  }

  def chunks(indents: Int, context: List[String]) = {

    val baseChunks = withDefined(base) {
      case n: SNullDisplay => Nil
      case b => b.chunks(indents, Nil) :+ StaticDisplayItem(".", "")
    }

    val (maxId, calculatedArgs) = CallArg.calculateIndexes(id, args, RubyHighlightType.SendArg)

    val maybeNewLine = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil)
    val maybeNewLine2 = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents) :: Nil)

    val argsIndents = if(multiline) {
      indents + DisplayChunk.TabSize
    } else {
      indents
    }

    val argsChunks: List[DisplayChunk] = ifNonEmpty(calculatedArgs) {
      List(
        DisplayItem(id, "(", HighlightType.WhiteBase, Nil, displayType = Some("call"), suppressHighlight = true) :: Nil,
        maybeNewLine,
        calculatedArgs.flatMap { arg => 
          if (multiline) {
            arg.chunks(argsIndents, context) ++ List(StaticDisplayItem.Comma, StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize))
          } else {
            arg.chunks(indents, context) :+ StaticDisplayItem.Comma
          }
        },
        EmptyCallArgDisplay(id, isNew = true, index = maxId.map(_ + 1).getOrElse(1), RubyHighlightType.SendArg).chunks(argsIndents, context),
        maybeNewLine2,
        DisplayItem(id, ")", HighlightType.WhiteBase, Nil, displayType = Some("call"), suppressHighlight = true) :: Nil
      ).flatten
    }

    val multilineHighlight = {
      val l1 = s"${baseChunks.flatMap(_.display).mkString}"
      val l2 = s"(${maybeNewLine.flatMap(_.display).mkString}"
      val l3 = s"${argsChunks.flatMap(_.display).mkString}...${maybeNewLine2.flatMap(_.display).mkString})"
      l1 + l2 + l3
    }

    List(
      DisplayItem(id, " ", HighlightType.Empty, Nil, displayType = None, multilineHighlight = Some(multilineHighlight)) :: Nil,
      baseChunks,
      withDefined(name) { n =>
        Option(DisplayItem(id, n, HighlightType.WhiteName, Nil, displayType = Some("send")).copy(
          canGroup = true,
          suppressHighlight = false
        ) :: Nil)
      }.getOrElse(DisplayItem.EmptyName(id, "send").copy(
        `type` = HighlightType.WhiteName,
        canGroup = true,
        suppressHighlight = false
      ) :: Nil),
      argsChunks
    ).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val args = children.getOrElse(RubyBuilderEdgeType.SendArg, Nil)
    
    DisplayContainer.applyContainer(
      this.copy(args = this.args ++ args),
      children - RubyBuilderEdgeType.SendArg
    )
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      base = base.map(_.replaceReferences(refMap)),
      args = args.map { arg => 
        arg.copy(struct = arg.struct.replaceReferences(refMap))
      }
    )
  }

}
