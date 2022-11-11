package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class MemberType(val identifier: String) extends Identifiable {
  def extract[T: Reads](data: JsValue) = {
    (data \ "terminus" \ "node" \ identifier).as[T]
  }
}

object MemberType extends Plenumeration[MemberType] {
  case object Name extends MemberType("name")
  case object Id extends MemberType("id")
  // case object Extracted extends MemberType("extracted")
}

sealed abstract class RelationalSelect(val resultType: QueryResultType) {
  def key: String

  def applySelect(data: Map[String, JsValue]): JsValue

  def columns: List[String]
}

sealed abstract class OperationType(val identifier: String, val resultType: QueryResultType, val aggregate: Boolean) extends Identifiable {
  def applyOperation(in: List[JsValue]): JsValue
}

object OperationType extends Plenumeration[OperationType] {
  case object CAT extends OperationType("CAT", QueryResultType.String, aggregate = false) {
    def applyOperation(in: List[JsValue]): JsValue = {
      val raw = in.map {
        case JsString(v) => v
        case o           => throw new Exception(s"${Json.stringify(o)} is not a string")
      }.mkString("")

      JsString(raw)
    }
  }

  // GROUP
  case object COUNT extends OperationType("COUNT", QueryResultType.Number, aggregate = true) {
    def applyOperation(in: List[JsValue]): JsValue = {
      Json.toJson(in.length)
    }
  }
}

object RelationalSelect {
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

      memberType.extract[JsValue](colData)
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

    def toGrouped = GroupedOperation(name, opType, operands)
  }

  // transforms into this?
  case class GroupedOperation(name: Option[String], opType: OperationType, operands: List[RelationalSelect]) {
    def key = name.getOrElse(Hashing.uuid())

    def applyGrouped(previous: JsValue, data: Seq[Map[String, JsValue]]): JsValue = {

      // Operand conversion (this is not specific to any particular operator?)
      val dataIn = data.toList map (_ => JsNull)
      val applied = opType.applyOperation(dataIn)

      // TODO: combination logic
      (previous, applied) match {
        case (JsNumber(inner), JsNumber(inner2)) => JsNumber(inner + inner2)
        case (JsNull, other)                     => other
        case _                                   => applied
      }
    }
  }

  // case class Operation(opType: OperationType, operands: List[RelationalSelect]) extends RelationalSelect
}
