package models.query

import silvousplay.imports._
import play.api.libs.json._
import models.graph.GenericGraphProperty

sealed abstract class ConditionType(val identifier: String) extends Identifiable

object ConditionType extends Plenumeration[ConditionType] {
  case object Index extends ConditionType("index")
  case object Name extends ConditionType("name")
  case object Prop extends ConditionType("prop")
}

sealed abstract class Condition(val `type`: ConditionType) {
  def filter: NodeFilter
  def toJson: JsValue

  def dto = ConditionDTO(
    `type`,
    toJson)
}
case class IndexCondition(index: Int) extends Condition(ConditionType.Index) {
  def filter = NodeIndexFilter(index - 1)

  def toJson = Json.toJson(index)
}

case class NameCondition(name: String) extends Condition(ConditionType.Name) {
  def filter = NodeNameFilter(name)

  def toJson = Json.toJson(name)
}

case class GraphPropertyCondition(props: List[GenericGraphProperty]) extends Condition(ConditionType.Prop) {
  def filter = NodePropsFilter(props)

  def toJson = Json.toJson(props)
}

case class ConditionDTO(`type`: ConditionType, value: JsValue) {
  def toModel = `type` match {
    case ConditionType.Index => IndexCondition(value.as[Int])
    case ConditionType.Name  => NameCondition(value.as[String])
    case ConditionType.Prop  => GraphPropertyCondition(value.as[List[GenericGraphProperty]])
  }
}

object ConditionDTO {
  implicit val format = Json.format[ConditionDTO]
}
