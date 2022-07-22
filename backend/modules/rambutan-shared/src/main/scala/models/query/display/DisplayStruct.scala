package models.query.display

import models.query._
import silvousplay.imports._
import play.api.libs.json._

// used for applyChildren
case class DisplayStructContainer(struct: DisplayStruct, name: Option[String], index: Option[Int]) {

  def filterEmpty: Option[DisplayStructContainer] = {
    struct.filterEmpty.headOption map { inn =>
      this.copy(struct = inn)
    }
  }
}

trait DisplayStruct {
  val id: String

  val alias: Option[String] = None

  // to implement
  def chunks(indents: Int, context: List[String]): List[DisplayChunk]

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct

  val forceReference: Boolean = false
  def forceDependency(dependencySet: Set[String]): DisplayStruct = this

  val multiline: Boolean = false

  val traverse: Option[HighlightType] = None

  // final pass stuff
  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct

  def containsReference(refId: String): Boolean = {
    this match {
      case ReferenceDisplay(rid, _, _, _) => rid =?= refId
      case other                          => other.children.exists(_.containsReference(refId))
    }
  }

  def filterEmpty: List[DisplayStruct] = List(this)

  // children stuff
  def children: List[DisplayStruct]

  def flattenedChildren: List[DisplayStruct] = {
    this :: children.flatMap(_.flattenedChildren)
  }

  // child -> parent
  def bodyMap(nodeSet: Set[String]): Map[String, String]
}

trait TraverseDisplayStruct extends DisplayStruct {
  val base: Option[DisplayStruct]

  def copyBase(newBase: DisplayStruct): DisplayStruct
}

trait BodyStruct {
  self: DisplayStruct =>

  val body: List[DisplayStruct]

  def bodyMap(nodeSet: Set[String]) = {
    println(body.flatMap(_.flattenedChildren))
    body.flatMap(_.flattenedChildren).map(_.id).filter(nodeSet.contains).map { b =>
      b -> self.id
    }.toMap
  }
}

trait NoBodyStruct {
  self: DisplayStruct =>

  def bodyMap(nodeSet: Set[String]) = {
    children.map(_.bodyMap(nodeSet)).foldLeft(Map.empty[String, String])(_ ++ _)
  }
}

trait ContainerStruct extends DisplayStruct with NoBodyStruct {

  override def flattenedChildren: List[DisplayStruct] = {
    children.flatMap(_.flattenedChildren)
  }
}

trait NoChildrenDisplayStruct extends DisplayStruct {

  def applyChildren(children: Map[BuilderEdgeType, List[DisplayStructContainer]]): DisplayStruct = {
    DisplayContainer.applyContainer(this, children)
  }

  def replaceReferences(refMap: Map[String, DisplayStruct]): DisplayStruct = this

  override def children: List[DisplayStruct] = Nil

  override def bodyMap(nodeSet: Set[String]): Map[String, String] = Map.empty[String, String]
}