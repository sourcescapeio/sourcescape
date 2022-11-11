package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class MemberType(val identifier: String) extends Identifiable {

}

object MemberType extends Plenumeration[MemberType] {
  case object Name extends MemberType("name")
  case object Code extends MemberType("code")
}

sealed abstract class RelationalSelect(val resultType: QueryResultType) {
  def key: String

  def applySelect(data: Map[String, JsValue]): JsValue

  def columns: List[String]
}

sealed abstract class OperationType(val identifier: String, val resultType: QueryResultType) extends Identifiable {
  def applyOperation(in: List[JsValue]): JsValue
}

object OperationType extends Plenumeration[OperationType] {
  case object CAT extends OperationType("CAT", QueryResultType.String) {
    def applyOperation(in: List[JsValue]): JsValue = {
      val raw = in.map {
        case JsString(v) => v
        case o           => throw new Exception(s"${Json.stringify(o)} is not a string")
      }.mkString("")

      JsString(raw)
    }
  }
}

object RelationalSelect {
  // case object CountAll extends RelationalSelect("count") {
  //   override val isDiff = true
  // }

  // case object SelectAll extends RelationalSelect("star")
  // case class Select(columns: List[String]) extends RelationalSelect("select")

  // For A
  case class Column(id: String) extends RelationalSelect(QueryResultType.GraphTrace) {
    def key = id

    def applySelect(data: Map[String, JsValue]): JsValue = {
      data.getOrElse(id, throw new Exception(s"Column ${id} not found in data"))
    }

    def columns = List(id)
  }

  // For A.name
  case class Member(name: Option[String], column: Column, memberType: MemberType) extends RelationalSelect(QueryResultType.String) {
    def key = name.getOrElse(s"${column.key}.${memberType.identifier}")

    def applySelect(data: Map[String, JsValue]): JsValue = {
      val colData = column.applySelect(data)

      (colData \ "terminus" \ "node" \ memberType.identifier).as[JsValue]
    }

    def columns = column.columns
  }

  // For CAT(...)
  case class Operation(name: Option[String], opType: OperationType, operands: List[RelationalSelect]) extends RelationalSelect(opType.resultType) {
    def key = name.getOrElse(Hashing.uuid())

    def applySelect(data: Map[String, JsValue]): JsValue = {
      val inputs = operands.map(_.applySelect(data))

      opType.applyOperation(inputs)
    }

    def columns = operands.flatMap(_.columns)
  }

  // case class Operation(opType: OperationType, operands: List[RelationalSelect]) extends RelationalSelect
}
