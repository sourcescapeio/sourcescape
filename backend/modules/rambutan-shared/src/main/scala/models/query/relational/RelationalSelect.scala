package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class MemberType(val identifier: String) extends Identifiable {
  def extract(data: JsValue) = {
    data match {
      case JsNull => JsNull
      case _      => (data \ "terminus" \ "node" \ identifier).asOpt[JsValue].getOrElse(JsNull)
    }
  }
}

object MemberType extends Plenumeration[MemberType] {
  case object Name extends MemberType("name")
  case object Id extends MemberType("id")
  case object Extracted extends MemberType("extracted")

  case object Path extends MemberType("path")
  case object Repo extends MemberType("repo")
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
        case JsNull      => ""
        case o           => throw new Exception(s"${Json.stringify(o)} is not a string")
      }.mkString("")

      JsString(raw)
    }
  }

  case object PATHCAT extends OperationType("PATHCAT", QueryResultType.String) {
    def applyOperation(in: List[JsValue]): JsValue = {
      val raw = in.map {
        case JsString(v) => {
          val withoutQuotes = v.replaceAll("\"", "")
          withoutQuotes
        }
        case JsNull => ""
        case o      => throw new Exception(s"${Json.stringify(o)} is not a string")
      }.mkString("/")

      JsString(raw)
    }
  }
}

sealed abstract class GroupedOperationType(val identifier: String, val resultType: QueryResultType) extends Identifiable {
  def applyGrouped(previous: JsValue, in: List[JsValue]): JsValue
}

object GroupedOperationType extends Plenumeration[GroupedOperationType] {
  case object COUNT extends GroupedOperationType("COUNT", QueryResultType.Number) {
    def applyGrouped(previous: JsValue, in: List[JsValue]): JsValue = {
      val prevCount = previous match {
        case JsNumber(inner) => inner
        case JsNull          => BigDecimal(0)
      }

      Json.toJson(prevCount + in.length)
    }
  }

  case object COLLECT extends GroupedOperationType("COLLECT", QueryResultType.GraphTraceList) {
    def applyGrouped(previous: JsValue, in: List[JsValue]): JsValue = {
      val joined = previous.asOpt[List[JsValue]].getOrElse(Nil) ++ in
      Json.toJson(joined)
    }
  }
}

object RelationalSelect {
  // For A
  case class Column(id: String) extends RelationalSelect(QueryResultType.GraphTrace) {
    def key = id

    def applySelect(data: Map[String, JsValue]): JsValue = {
      // data.getOrElse(id, throw new Exception(s"Column ${id} not found in data"))
      data.getOrElse(id, JsNull)
    }

    def columns = List(id)
  }

  // For A.name
  case class Member(name: Option[String], column: Column, memberType: MemberType) extends RelationalSelect(QueryResultType.String) {
    def key = name.getOrElse(s"${column.key}.${memberType.identifier}")

    def applySelect(data: Map[String, JsValue]): JsValue = {
      val colData = column.applySelect(data)

      memberType.extract(colData)
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

  // transforms into this?
  case class GroupedOperation(name: Option[String], opType: GroupedOperationType, operands: List[RelationalSelect]) extends RelationalSelect(opType.resultType) {
    def key = name.getOrElse(Hashing.uuid())

    def applySelect(data: Map[String, JsValue]): JsValue = {
      throw new Exception("not implemented")
    }

    def applyGrouped(previous: JsValue, data: Seq[Map[String, JsValue]]): JsValue = {

      // Operand conversion (this is not specific to any particular operator?)
      val dataIn = data.toList flatMap { d =>
        operands.map(_.applySelect(d))
      }
      opType.applyGrouped(previous, dataIn)
    }

    def columns = operands.flatMap(_.columns)
  }

  // case class Operation(opType: OperationType, operands: List[RelationalSelect]) extends RelationalSelect
}
