package models.index.scalameta

import models.index.{ EdgeType, NodeType }
import silvousplay.imports._

sealed abstract class ScalaMetaEdgeType(val identifier: String) extends EdgeType

// trait ContainsEdgeType {
//   self: ScalaMetaEdgeType =>

//   override val isContains = true
// }

object ScalaMetaEdgeType extends Plenumeration[ScalaMetaEdgeType] {

}

sealed abstract class ScalaMetaNodeType(val identifier: String) extends NodeType

object ScalaMetaNodeType extends Plenumeration[ScalaMetaNodeType] {

  case object Trait extends ScalaMetaNodeType("trait")

  case object Object extends ScalaMetaNodeType("object")
}
