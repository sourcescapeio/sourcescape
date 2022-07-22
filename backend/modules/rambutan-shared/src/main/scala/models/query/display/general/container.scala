package models.query.display

import models.query._
import silvousplay.imports._
import play.api.libs.json._

// used only to wipe out head
// only an output strut
case class EmptyContainer(branches: List[DisplayStruct]) extends ContainerStruct {

  def children: List[DisplayStruct] = branches

  val id = ""

  def chunks(indents: Int, context: List[String]) = {
    // drop right as hack around real intersperse
    DisplayContainer.applyTraverses(branches, indents, context)
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    this
  }

  // Don't do anything because this is only an output struct
  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = this

  override def filterEmpty = {
    ifNonEmpty(branches) {
      this :: Nil
    }
  }
}

case class DisplayContainer(head: DefineContainer, branches: List[DisplayStruct]) extends ContainerStruct {
  override val alias = head.alias.orElse(Some(head.id))

  val id = head.id

  def children: List[DisplayStruct] = head :: branches

  def chunks(indents: Int, context: List[String]) = {
    // special case for function args (no need to redefine)
    // `arg := arg; arg;` => just `arg`
    val functionArg = head.inner match {
      case FunctionArgDisplay(_, _, _, _) => true
      case _                              => false
    }

    val base = if (functionArg) {
      branches
    } else {
      head :: branches
    }
    // drop right as hack around real intersperse
    DisplayContainer.applyTraverses(base, indents, context)
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    DisplayContainer.applyContainer(this, children)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    val newBranches = branches.map(_.replaceReferences(refMap))

    if (refMap.contains(head.id)) {
      EmptyContainer(newBranches)
    } else {
      this.copy(
        head = head.replaceReferences(refMap).asInstanceOf[DefineContainer], // DANGER
        branches = newBranches)
    }
  }

  override def forceDependency(dependencySet: Set[String]): DisplayStruct = {
    this.copy(
      head = head.forceDependency(dependencySet).asInstanceOf[DefineContainer])
  }

  override def filterEmpty = {
    this.copy(branches = branches.flatMap(_.filterEmpty)) :: Nil
  }
}

case class ReferenceDisplay(id: String, aliasIn: Option[String], parentId: Option[String], replace: Boolean) extends ContainerStruct {

  def children: List[DisplayStruct] = Nil

  override val alias = aliasIn

  def chunks(indents: Int, context: List[String]) = {
    DisplayItem(id, alias.getOrElse(id), HighlightType.GoldBase, Nil, displayType = Some("ref"), edgeFrom = parentId) :: Nil
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    this
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    withFlag(replace) {
      refMap.get(id)
    }.getOrElse(this)
  }
}

case class DefineContainer(inner: DisplayStruct) extends ContainerStruct {

  def children: List[DisplayStruct] = inner :: Nil

  val id = inner.id

  override val alias = inner.alias

  def ref = ReferenceDisplay(id, alias, None, replace = false)

  def chunks(indents: Int, context: List[String]) = {
    val innerChunks = inner.chunks(indents, context)
    val multilineHighlight = {
      s"${alias.getOrElse(id)} := ${innerChunks.flatMap(_.display).mkString}"
    }

    List(
      DisplayItem(id, alias.getOrElse(id), HighlightType.GoldBase, Nil, displayType = None, multilineHighlight = Some(multilineHighlight)) :: Nil,
      StaticDisplayItem(" := ", "") :: Nil,
      innerChunks.map {
        case c: DisplayItem if c.id =?= id => c.copy(suppressHighlight = true)
        //multilineHighlight = None,
        case c                             => c
      }).flatten
  }

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    this
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = {
    this.copy(
      inner = inner.replaceReferences(refMap))
  }

  override def forceDependency(dependencySet: Set[String]): DisplayStruct = {
    this.copy(
      inner = inner.forceDependency(dependencySet))
  }
}

object DisplayContainer {

  def applyTraverses(branches: List[DisplayStruct], indents: Int, context: List[String]) = {
    // drop right as hack around real intersperse
    ifNonEmpty(branches) {
      branches.flatMap { b =>
        b.chunks(indents, context) ++ withDefined(b.traverse) { t =>
          DisplayItem(b.id, ";  ", t, Nil, displayType = None, replace = true) :: Nil
        } :+ StaticDisplayItem.NewLine(2, indents)
      }.dropRight(1)
    }
  }

  def applyBase(child: DisplayStruct, head: DisplayStruct): DisplayStruct = {
    child match {
      case d: DisplayContainer => {
        // define container replace
        d.copy(head = DefineContainer(applyBase(d.head.inner, head)))
      }
      // special case for traverses
      case t: TraverseDisplayStruct => {
        t.base match {
          case Some(inner) => t.copyBase(applyBase(inner, head))
          case None        => t.copyBase(head)
        }
      }
      case _ => head
    }
  }

  def forceContainer(head: DisplayStruct, children: Map[BuilderEdgeType, List[DisplayStructContainer]]) = {
    // remove traverses
    val fullChildren = children.filterNot(_._1.isTraverse)
    val define = DefineContainer(head.applyChildren(fullChildren))

    val traverseChildren = children.flatMap {
      case (k, r) if k.isTraverse => r
      case _                      => Nil
    }.toList

    val child = traverseChildren.toList.map { c =>
      applyBase(c.struct, define.ref)
    }

    // apply id to children
    DisplayContainer(define, child)
  }

  def applyContainer(head: DisplayStruct, children: Map[BuilderEdgeType, List[DisplayStructContainer]]) = {
    children.values.flatten.toList match {
      case Nil => head
      case single :: Nil if head.alias.isEmpty && !head.forceReference => {
        applyBase(single.struct, head)
      }
      case _ => {
        forceContainer(head, children)
      }
    }
  }
}
