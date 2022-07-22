package models.query.display

import models.query._
import models.query.grammar._
import silvousplay.imports._

case class DepDisplay(id: String, parentId: String, dep: Option[String]) extends NoChildrenDisplayStruct {

  def chunks(indents: Int, context: List[String]) = {
    val multilineHighlight = "dep[" + dep.getOrElse("A") + "]"
    List(
      DisplayItem(id, "dep", HighlightType.WhiteBase, context = Nil, displayType = Some("dep"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      StaticDisplayItem("[", "") :: Nil,
      withDefined(dep) { d =>
        DisplayItem(id, d, HighlightType.OrangeBase, context = Nil, displayType = Some("dep"), suppressHighlight = true) :: Nil
      },
      StaticDisplayItem("]", "") :: Nil).flatten
    // DisplayItem(id, "?", HighlightType.WhiteBase, context).copy(
    //   canGroup = true
    // ) :: Nil
  }

  override def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val dep = children.getOrElse(BuilderEdgeType.RequireDependency, Nil).headOption.map(_.struct.id)

    DisplayContainer.applyContainer(
      this.copy(dep = dep),
      children - BuilderEdgeType.RequireDependency)
  }
}

case class AnyDisplay(id: String, parentId: String, isDependency: Boolean) extends NoChildrenDisplayStruct {

  override val traverse = Some(HighlightType.Traverse)

  override val forceReference = isDependency

  override def forceDependency(dependencySet: Set[String]): DisplayStruct = {
    this.copy(isDependency = dependencySet.contains(id))
  }

  def chunks(indents: Int, context: List[String]) = {
    if (isDependency) {
      DisplayItem(id, "_____", HighlightType.Any, Nil, displayType = Some("any"), canGroup = true, replace = true) :: Nil
    } else {
      DisplayItem(id, "?", HighlightType.WhiteBase, context, displayType = Some("any")).copy(
        canGroup = true) :: Nil
    }
  }
}

case class AnySearchDisplay(id: String, name: Option[String]) extends NoChildrenDisplayStruct {

  override val traverse = Some(HighlightType.Traverse)

  def chunks(indents: Int, context: List[String]) = {
    name match {
      case Some(n) => DisplayItem(id, n, HighlightType.WhiteName, context, displayType = Some("search")) :: Nil
      case _       => DisplayItem(id, "?", HighlightType.WhiteName, context, displayType = Some("search")) :: Nil
    }
  }
}

case class EmptyCallArgDisplay(parentId: String, isNew: Boolean, index: Int, connecting: HighlightType) extends NoChildrenDisplayStruct {
  val id = parentId

  def chunks(indents: Int, context: List[String]) = {
    val emptyStr = if (isNew) "..." else "___"
    List(
      DisplayItem(id, emptyStr, connecting, context = context, displayType = None, index = Some(index)))
  }
}

case class CallArg(id: String, parentId: String, inner: DisplayStruct, index: Option[Int]) extends DisplayStruct with NoBodyStruct {

  def children = inner :: Nil

  override val multiline = inner.multiline

  def chunks(indents: Int, context: List[String]) = {
    inner.chunks(indents, context) :+ DisplayItem(
      id,
      index.map(_.toString).getOrElse("*"),
      HighlightType.IndexOnly,
      Nil,
      displayType = Some("call-arg"),
      index = index,
      parentId = Some(parentId))
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    this
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(inner = inner.replaceReferences(refMap))
  }
}

object CallArg {
  def calculateIndexes(id: String, args: List[DisplayStructContainer], connecting: HighlightType): (Option[Int], List[DisplayStruct]) = {
    val maxId = args.flatMap(_.index).maxByOption(i => i)

    val argMap = args.flatMap {
      case DisplayStructContainer(st, _, Some(idx)) => Some(idx -> CallArg(st.id, id, st, Some(idx)))
      case _                                        => None
    }.toMap

    val noIdxArgs = args.flatMap {
      case DisplayStructContainer(st, _, None) => Some(CallArg(st.id, id, st, None))
      case _                                   => None
    }

    val filledArgs = withDefined(maxId) { m =>
      Range(1, m + 1).toList.map { i =>
        argMap.get(i).getOrElse {
          // need completely separate struct
          EmptyCallArgDisplay(id, isNew = false, index = i, connecting)
        }
      }
    } ++ noIdxArgs

    (maxId, filledArgs)
  }
}