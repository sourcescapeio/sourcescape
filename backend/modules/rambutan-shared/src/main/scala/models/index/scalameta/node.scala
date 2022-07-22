package models.index.scalameta

import models.index._
import models.{ CodeRange }
import silvousplay.imports._
import play.api.libs.json._

sealed trait ScalaMetaNode extends GraphNodeData[ScalaMetaNodeType]

sealed abstract class ScalaMetaTag(val identifier: String) extends Identifiable

sealed abstract class ScalaMetaNodeBuilder(
  val nodeType:    ScalaMetaNodeType,
  graphName:       Option[String]    = None,
  additionalNames: List[String]      = Nil,
  graphIndex:      Option[Int]       = None) extends ScalaMetaNode with StandardNodeBuilder[ScalaMetaNodeType, ScalaMetaTag] {

  val names = graphName.toList ++ additionalNames

  val index = graphIndex

  val tags: List[ScalaMetaTag] = Nil
}

/**
 * Impl
 */
case class TraitNode(id: String, range: CodeRange, name: String) extends ScalaMetaNodeBuilder(ScalaMetaNodeType.Trait, graphName = Some(name))

case class ObjectNode(id: String, range: CodeRange, name: String) extends ScalaMetaNodeBuilder(ScalaMetaNodeType.Object, graphName = Some(name))
