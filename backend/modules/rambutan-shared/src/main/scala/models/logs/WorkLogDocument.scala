package models

import silvousplay.imports._
import play.api.libs.json._
import org.joda.time._

sealed abstract class WorkLogType(val identifier: String) extends Identifiable

object WorkLogType extends Plenumeration[WorkLogType] {
  case object Global extends WorkLogType("global")
  case object Repo extends WorkLogType("repo")
  case object Path extends WorkLogType("path")
}

case class WorkLogDocument(
  work_id:  String,
  parents:  List[String],
  message:  String,
  is_error: Boolean,
  time:     Long) {

  def dto(logId: String) = WorkLogDocumentDTO(
    logId,
    work_id,
    parents,
    message,
    TimeFormatter.formatter.print(new DateTime(time)))
}

object WorkLogDocument {
  val globalIndex = "logs"

  val mappings = Json.obj(
    "mappings" -> Json.obj(
      "dynamic" -> "strict",
      "properties" -> Json.obj(
        "work_id" -> Json.obj(
          "type" -> "keyword"),
        "parents" -> Json.obj(
          "type" -> "keyword"),
        "message" -> Json.obj(
          "type" -> "object",
          "enabled" -> false),
        "is_error" -> Json.obj(
          "type" -> "boolean"),
        "time" -> Json.obj(
          "type" -> "long"))))

  implicit val format = Json.format[WorkLogDocument]
}

case class WorkLogDocumentDTO(
  logId:   String,
  workId:  String,
  parents: List[String],
  message: String,
  time:    String)

object WorkLogDocumentDTO {
  implicit val writes = Json.writes[WorkLogDocumentDTO]
}
