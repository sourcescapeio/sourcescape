package models.query.display.javascript

import models.query._
import models.query.display._
import silvousplay.imports._
import play.api.libs.json._

case class ClassDisplay(id: String, name: Option[String], properties: List[DisplayStruct], methods: List[DisplayStruct], extend: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = extend.toList ++ methods  

  override val forceReference = true

  private def nameDisplay = withDefined(name) { n =>
    Option {
      DisplayItem.Name(id, n, "class")
    }
  }.getOrElse(DisplayItem.EmptyName(id, "class"))

  override def filterEmpty = {
    this.copy(methods = methods.flatMap(_.filterEmpty)) :: Nil
  }  

  def chunks(indents: Int, context: List[String]) = {

    val extendsChunks = withDefined(extend) { e =>
      Option(e.chunks(indents, context))
    }.getOrElse(DisplayItem.EmptyItem(id, JavascriptHighlightType.ClassExtends, Nil) :: Nil)

    val propertiesChunks = properties.flatMap { p => 
      p.chunks(indents + DisplayChunk.TabSize, context) :+ StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize)
    }

    val methodsChunks = methods.flatMap { m => 
      m.chunks(indents + DisplayChunk.TabSize, context) :+ StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize)
    }

    val multilineHighlight = {
      Option {
        val l1 = s"class ${nameDisplay.display} extends ${extendsChunks.flatMap(_.display).mkString} {\n\n    "
        val l2 = s"${propertiesChunks.flatMap(_.display).mkString}"
        val l3 = s"${methodsChunks.flatMap(_.display).mkString}"
        val l4 = s"  ..."
        val l5 = s"\n\n  }"
        l1 + l2 + l3 + l4 + l5
      }
    }

    val extendKey = DisplayItem(extend.map(_.id).getOrElse(id), "extends", HighlightType.RedBase, Nil, displayType = Some("class")).copy(
      edgeFrom = extend.map(_ => id),
      suppressHighlight = true
    )

    List(
    DisplayItem(id, "class", HighlightType.BlueBase, Nil, displayType = Some("class"), multilineHighlight = multilineHighlight) :: Nil,
    StaticDisplayItem.Space :: Nil,
    nameDisplay.copy(canGroup = true) :: Nil,
    StaticDisplayItem.Space :: Nil,
    extendKey :: Nil,
    StaticDisplayItem.Space :: Nil,
    extendsChunks,
    StaticDisplayItem.Space :: Nil,
    StaticDisplayItem.OpenBracket :: Nil,
    StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize) :: Nil,
    ifNonEmpty(propertiesChunks) {
      propertiesChunks
    },
    ifNonEmpty(methodsChunks) {
      methodsChunks
    },
    DisplayItem.EmptyBody(id, JavascriptHighlightType.ClassBody, context) :: Nil,
    StaticDisplayItem.NewLine(2, indents) :: Nil,
    StaticDisplayItem.CloseBracket :: Nil).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val extend = children.getOrElse(JavascriptBuilderEdgeType.ClassExtends, Nil).headOption.map(_.struct)
    val properties = children.getOrElse(JavascriptBuilderEdgeType.ClassProperty, Nil).map(_.struct)
    val methods = children.getOrElse(JavascriptBuilderEdgeType.ClassMethod, Nil).map(_.struct)

    DisplayContainer.applyContainer(
      this.copy(
        properties = this.properties ++ properties,
        methods = this.methods ++ methods,
        extend = extend.orElse(this.extend)),
      children - JavascriptBuilderEdgeType.ClassExtends - JavascriptBuilderEdgeType.ClassMethod - JavascriptBuilderEdgeType.ClassProperty
    )
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      properties = properties.map(_.replaceReferences(refMap)),
      methods = methods.map(_.replaceReferences(refMap)),
      extend = extend.map(_.replaceReferences(refMap))
    )
  }
}

case class ClassPropertyDisplay(id: String, name: Option[String], inner: Option[DisplayStruct]) extends DisplayStruct with NoBodyStruct{ 

  override val multiline = inner.map(_.multiline).getOrElse(false)

  def children: List[DisplayStruct] = inner.toList

  private def nameDisplay = withDefined(name) { n =>
    Option {
      DisplayItem.Name(id, n, "class-property")
    }
  }.getOrElse(DisplayItem.EmptyName(id, "class-property"))

  def chunks(indents: Int, context: List[String]) = {
    val innerDisplay = inner.map(_.chunks(indents + DisplayChunk.TabSize, context)).getOrElse { 
      DisplayItem.EmptyBody(id, JavascriptHighlightType.ClassPropertyValue, context) :: Nil
    }

    val multilineHighlight = {
      Option {
        val l1 = s"${nameDisplay.display} = "
        val l2 = withFlag(multiline) {
          "\\\n"
        }
        val l3 = innerDisplay.flatMap(_.display).mkString
        l1 + l2 + l3
      }
    }

    List(
      nameDisplay.copy(multilineHighlight = multilineHighlight, canGroup = true) :: Nil,
      List(StaticDisplayItem.Space, StaticDisplayItem.Equals, StaticDisplayItem.Space),
      withFlag(multiline) {
        List(
          StaticDisplayItem("\\", ""),
          StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize)
        )
      },
      innerDisplay
    ).flatten  
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val inner = children.getOrElse(JavascriptBuilderEdgeType.ClassPropertyValue, Nil).headOption.map(_.struct)

    DisplayContainer.applyContainer(
      this.copy(inner = inner),
      children - JavascriptBuilderEdgeType.ClassPropertyValue)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.map(_.replaceReferences(refMap))
    )
  }
}

//args: List[ArgDisplay], body: List[DisplayStruct], ret: Option[ReturnDisplay]
case class ClassMethodDisplay(id: String, name: Option[String], args: List[DisplayStruct], body: List[DisplayStruct]) extends DisplayStruct with BodyStruct {

  def children: List[DisplayStruct] = body  // explicitly ignoring args because can only be FunctionArg

  override def filterEmpty = {
    this.copy(body = body.flatMap(_.filterEmpty)) :: Nil
  }

  private def nameDisplay = withDefined(name) { n =>
    Option {
      DisplayItem.Name(id, n, "class-method")
    }
  }.getOrElse(DisplayItem.EmptyName(id, "class-method"))

  def chunks(indents: Int, context: List[String]) = {
    val fullContext = context ++ args.map(_.id)

    val (maxId, argsChunks) = FunctionArgs.calculateArgs(id, JavascriptHighlightType.EmptyMethodArg, args, indents)

    val bodyChunks = FunctionBody.applyChunks(body, indents, fullContext, spacing = 1)

    val multilineHighlight = {
      Option {
        val l1 = s"${nameDisplay.display}(${argsChunks.map(_.display).mkString}...){"
        val l2 = ifNonEmpty(body)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize).display)
        //${returnsChunks.flatMap(_.display)}
        val l3 = s"${bodyChunks.flatMap(_.display).mkString}\n\n...\n\n}"
        l1 + l2 + l3
      }
    }    

    List(
      nameDisplay.copy(multilineHighlight = multilineHighlight, canGroup = true) :: Nil,      
      StaticDisplayItem.OpenParens :: Nil,
      argsChunks,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.EmptyMethodArg, Nil).copy(
        index = Some(maxId.map(_ + 1).getOrElse(1))) :: Nil,
      StaticDisplayItem.CloseParens :: Nil,
      StaticDisplayItem.OpenBracket :: Nil,
      ifNonEmpty(body)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil),
      bodyChunks,
      // ifNonEmpty(ret)(StaticDisplayItem.NewLine(2, indents + 4) :: Nil),
      // withDefined(ret)(r => r.chunks(indents)),    
      StaticDisplayItem.NewLine(2, indents + DisplayChunk.TabSize) :: Nil,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.MethodBody, fullContext) :: Nil,
      StaticDisplayItem.NewLine(2, indents) :: Nil,
      StaticDisplayItem.CloseBracket :: Nil,
    ).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val body = children.getOrElse(JavascriptBuilderEdgeType.MethodContains, Nil).map(_.struct)

    val argTuples = children.getOrElse(JavascriptBuilderEdgeType.MethodArg, Nil).map(_.struct) map {
      case f: FunctionArgDisplay => {
        (Nil, f.copy(parentId = Some(this.id)))
      }
      case d: DisplayContainer => {
        // dedupe against contains
        (d.branches, d.head.inner)
      }
      case _ => throw new Exception("invalid child for method arg")
    }

    val argBody = argTuples.flatMap(_._1)
    val args = argTuples.map(_._2)

    DisplayContainer.applyContainer(
      this.copy(
        args = this.args ++ args,
        body = this.body ++ body ++ argBody),
      children - JavascriptBuilderEdgeType.MethodContains - JavascriptBuilderEdgeType.MethodArg)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      body = FunctionBody.calculateBodyReplace(body, refMap)
    )
  }
}

case class ThisDisplay(id: String) extends DisplayStruct with NoChildrenDisplayStruct {

  override val traverse = Some(HighlightType.Traverse)

  def chunks(indents: Int, context: List[String]) = {
    DisplayItem(id, "this", HighlightType.OrangeBase, Nil, displayType = Some("this")) :: Nil
  }
}

case class SuperDisplay(id: String) extends DisplayStruct with NoChildrenDisplayStruct {

  def chunks(indents: Int, context: List[String]) = {
    DisplayItem(id, "super", HighlightType.OrangeBase, Nil, displayType = Some("super")) :: Nil
  }
}

case class InstanceDisplay(id: String, name: Option[DisplayStruct], args: List[DisplayStruct]) extends DisplayStruct with NoBodyStruct {

  def children: List[DisplayStruct] = name.toList ++ args  

  override def filterEmpty = {
    this.copy(
      name = name.flatMap(_.filterEmpty.headOption),
      args = args.flatMap(_.filterEmpty)
    ) :: Nil
  }

  override val traverse = Some(HighlightType.Traverse)

  override val multiline = args.exists(_.multiline)

  def chunks(indents: Int, context: List[String]) = {
    val nameChunks = withDefined(name) { n =>
      Option(n.chunks(indents, context))
    }.getOrElse(DisplayItem.EmptyItem(id, JavascriptHighlightType.InstanceOf, context) :: Nil)

    val argsChunks = args.flatMap { arg => 
      if (multiline) {
        (arg.chunks(indents + DisplayChunk.TabSize, context) ++ List(StaticDisplayItem.Comma, StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize)))
      } else {
        (arg.chunks(indents, context) :+ StaticDisplayItem.Comma)
      }
    }

    val maybeNewLine = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents + DisplayChunk.TabSize) :: Nil)
    val maybeNewLine2 = withFlag(multiline)(StaticDisplayItem.NewLine(1, indents) :: Nil)

    val multilineHighlight = {
      val l1 = s"new ${nameChunks.flatMap(_.display).mkString}(${maybeNewLine.flatMap(_.display).mkString}"
      val l2 = s"${argsChunks.flatMap(_.display).mkString}...${maybeNewLine2.flatMap(_.display).mkString})"
      l1 + l2
    }

    List(
      DisplayItem(id, "new", HighlightType.RedBase, Nil, displayType = Some("instance"), multilineHighlight = Some(multilineHighlight)) :: Nil,
      StaticDisplayItem.Space :: Nil,
      nameChunks,
      StaticDisplayItem.OpenParens :: Nil,
      maybeNewLine,
      //
      argsChunks,
      DisplayItem.EmptyBody(id, JavascriptHighlightType.InstanceArg, context) :: Nil,
      //
      maybeNewLine2,
      StaticDisplayItem.CloseParens :: Nil,
    ).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    val name = children.getOrElse(JavascriptBuilderEdgeType.InstanceOf, Nil).headOption.map(_.struct)
    val args = children.getOrElse(JavascriptBuilderEdgeType.InstanceArg, Nil).map(_.struct)

    DisplayContainer.applyContainer(
      this.copy(
        name = name.orElse(this.name), 
        args = this.args ++ args
      ), 
      children - JavascriptBuilderEdgeType.InstanceOf - JavascriptBuilderEdgeType.InstanceArg
    )
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      name = name.map(_.replaceReferences(refMap)),
      args = args.map(_.replaceReferences(refMap))
    )
  }
}
