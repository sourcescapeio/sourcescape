package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._

object FunctionArgs {

  def calculateArgs(id: String, parentType: HighlightType, args: List[DisplayStruct], indents: Int) = {
    val maxId = args.flatMap {
      case FunctionArgDisplay(_, _, _, idx) => idx
      case _                                => None
    }.maxByOption(i => i)

    val argMap = args.flatMap {
      case f @ FunctionArgDisplay(_, _, _, Some(idx)) => Some(idx -> f)
      case _ => None
    }.toMap

    val noIdxArgs = args.flatMap {
      case f @ FunctionArgDisplay(_, _, _, None) => Some(f)
      case _                                     => None
    }

    val argsFilled = withDefined(maxId) { m =>
      Range(1, m + 1).toList.map { i =>
        argMap.get(i).getOrElse {
          EmptyArgDisplay(id, parentType, i)
        }
      }
    } ++ noIdxArgs

    (maxId, argsFilled.flatMap(_.chunks(indents, Nil) :+ StaticDisplayItem.Comma))
  }
}

object FunctionBody {
  def calculateBodyReplace(body: List[DisplayStruct], refMap: Map[String, DisplayStruct]) = {
    // for first level references, we do a define replace
    body.map {
      case r: ReferenceDisplay => {
        if ((refMap - r.id).values.exists(_.containsReference(r.id))) {
          DefineContainer(r.replaceReferences(refMap))
        } else {
          r.replaceReferences(refMap)
        }
      }
      case o => o.replaceReferences(refMap)
    }
  }

  def applyChunks(body: List[DisplayStruct], indents: Int, context: List[String], spacing: Int) = {
    body.flatMap { b =>
      (StaticDisplayItem.NewLine(spacing, indents + DisplayChunk.TabSize) :: b.chunks(indents + DisplayChunk.TabSize, context)) ++ withDefined(b.traverse) { t =>
        //HighlightType.FunctionContainsTraverse
        DisplayItem(b.id, ";  ", t, Nil, displayType = None, replace = true) :: Nil
      }
    }
  }
}

case class FunctionDisplay(
  id:   String,
  name: Option[String],
  args: List[DisplayStruct],
  body: List[DisplayStruct]) extends DisplayStruct with BodyStruct {

  def children: List[DisplayStruct] = body

  override val multiline = true

  private def nameDisplay = withDefined(name) { n =>
    Option {
      DisplayItem.Name(id, n, "function")
    }
  }.getOrElse(DisplayItem.EmptyName(id, "function"))

  override def filterEmpty = {
    this.copy(body = body.flatMap(_.filterEmpty)) :: Nil
  }

  def chunks(indents: Int, context: List[String]) = {

    val fullContext = context ++ args.map(_.id)
    val (maxId, argsChunks) = FunctionArgs.calculateArgs(id, JavascriptHighlightType.EmptyFunctionArg, args, indents)
    // fill out args

    val bodyChunks = FunctionBody.applyChunks(body, indents, fullContext, spacing = 2)

    val multilineHighlight = {
      Option {
        val l1 = s"function ${nameDisplay.display}(${argsChunks.map(_.display).mkString}...){"
        //${returnsChunks.flatMap(_.display)}
        val l2 = s"${bodyChunks.map(_.display).mkString}\n\n...\n\n}"
        l1 + l2
      }
    }

    List(
      DisplayItem(id, s"function", HighlightType.BlueBase, Nil, displayType = Some("function"), multilineHighlight = multilineHighlight) :: Nil,
      StaticDisplayItem.Space :: Nil,
      nameDisplay.copy(canGroup = true) :: Nil,
      StaticDisplayItem.OpenParens :: Nil,
      argsChunks,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.EmptyFunctionArg, Nil).copy(
        index = Some(maxId.map(_ + 1).getOrElse(1))) :: Nil,
      StaticDisplayItem.CloseParens :: Nil,
      StaticDisplayItem.OpenBracket :: Nil,
      bodyChunks,
      StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize) :: Nil,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.FunctionBody, fullContext) :: Nil,
      // StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize) :: Nil,
      // returnsChunks,
      StaticDisplayItem.NewLine(2, indents) :: Nil,
      StaticDisplayItem.CloseBracket :: Nil).flatten
  }

  override val forceReference = true

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val body = children.getOrElse(JavascriptBuilderEdgeType.FunctionContains, Nil).map(_.struct)

    val argTuples = children.getOrElse(JavascriptBuilderEdgeType.FunctionArg, Nil).map(_.struct) map {
      case f: FunctionArgDisplay => {
        (Nil, f.copy(parentId = Some(this.id)))
      }
      case d: DisplayContainer => {
        // dedupe against contains
        (d.branches, d.head.inner)
      }
      case _ => throw new Exception("invalid child for function")
    }

    val argBody = argTuples.flatMap(_._1)
    val args = argTuples.map(_._2)

    DisplayContainer.applyContainer(
      this.copy(
        args = this.args ++ args,
        body = this.body ++ body ++ argBody),
      children - JavascriptBuilderEdgeType.FunctionArg - JavascriptBuilderEdgeType.FunctionContains)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    // function contains should implicity add this define
    println("BODY")
    println(this.body)

    this.copy(
      args = args.map(_.replaceReferences(refMap)),
      body = FunctionBody.calculateBodyReplace(body, refMap))
  }
}

case class FunctionReturnDisplay(id: String, highlightType: HighlightType, keyword: String, inner: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = inner.toList

  override def filterEmpty = {
    this.copy(
      inner = inner.flatMap(_.filterEmpty.headOption)) :: Nil
  }

  override val multiline = inner.exists(_.multiline)

  def chunks(indents: Int, context: List[String]) = {
    val additionalIndent = withFlag(multiline)(Option(DisplayChunk.TabSize)).getOrElse(0)

    val innerChunks = withDefined(inner) { i =>
      Option(i.chunks(indents + additionalIndent, context))
    }.getOrElse(DisplayItem.EmptyBody(id, highlightType, context) :: Nil)

    val dividerChunks = if (multiline) {
      List(StaticDisplayItem.Space, StaticDisplayItem("\\", ""))
    } else {
      StaticDisplayItem.Space :: Nil
    }

    val maybeNewLine = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil)

    val multilineHighlight = {
      val l1 = s"${keyword}${dividerChunks.flatMap(_.display)}"
      val l2 = s"${maybeNewLine.flatMap(_.display).mkString}${innerChunks.map(_.display).mkString}"
      l1 + l2
    }

    List(
      DisplayItem(id, keyword, HighlightType.RedBase, Nil, displayType = Some("return"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      dividerChunks,
      maybeNewLine,
      innerChunks).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val inner = {
      val rets = children.getOrElse(JavascriptBuilderEdgeType.Return, Nil).map(_.struct)
      val yields = children.getOrElse(JavascriptBuilderEdgeType.Yield, Nil).map(_.struct)
      val awaits = children.getOrElse(JavascriptBuilderEdgeType.Await, Nil).map(_.struct)
      val throws = children.getOrElse(JavascriptBuilderEdgeType.Throw, Nil).map(_.struct)
      rets ++ yields ++ awaits ++ throws
    }.headOption

    this.copy(
      inner = inner.orElse(this.inner))
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.map(_.replaceReferences(refMap)))
  }
}

private case class EmptyArgDisplay(parentId: String, parentType: HighlightType, index: Int) extends DisplayStruct with NoChildrenDisplayStruct {
  val id = parentId // DO NOT USE

  def chunks(indents: Int, context: List[String]) = {
    List(
      DisplayItem(id, "__", parentType, Nil, displayType = Some("arg"), index = Some(index)))
  }
}
