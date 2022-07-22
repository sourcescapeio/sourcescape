package models

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class WorkStatus(val identifier: String) extends Identifiable

object WorkStatus extends Plenumeration[WorkStatus] {
  case object Pending extends WorkStatus("pending")
  case object InProgress extends WorkStatus("in-progress")
  case object Complete extends WorkStatus("complete")
  case object Error extends WorkStatus("error")
  case object Skipped extends WorkStatus("skipped")
}

case class WorkRecord(
  id:       String,
  parents:  List[String],
  tags:     JsValue,
  orgId:    Int,
  status:   WorkStatus,
  started:  Option[Long],
  finished: Option[Long]) {

  // def unified = UnifiedWorkItem(id, status, tags)

  def child(newTags: JsValue) = {
    WorkRecord(
      Hashing.uuid(),
      parents :+ id,
      newTags,
      orgId,
      WorkStatus.Pending,
      None,
      None)
  }

  def hydrate(
    parents:    List[WorkRecord],
    children:   List[WorkRecord],
    skippedMap: Map[String, List[String]],
    errorMap:   Map[String, List[String]]) = {
    WorkTree(
      parents,
      this,
      children,
      skippedMap,
      errorMap)
  }

  def time = (started, finished) match {
    case (Some(s), Some(f)) => Some(f - s)
    case _                  => None
  }

  def dto = WorkRecordDTO(
    id,
    status,
    tags.as[JsObject],
    time,
    started.map(TimeFormatter.str),
    finished.map(TimeFormatter.str))

  def treeDTO(skippedMap: Map[String, List[String]], errorMap: Map[String, List[String]]) = {
    WorkTreeRecordDTO(
      id,
      status,
      tags.as[JsObject],
      started.map(TimeFormatter.str),
      finished.map(TimeFormatter.str),
      time,
      skippedMap.getOrElse(id, Nil),
      errorMap.getOrElse(id, Nil))
  }
}

case class WorkTreeRecordDTO(
  id:              String,
  status:          WorkStatus,
  tags:            JsObject,
  started:         Option[String],
  finished:        Option[String],
  time:            Option[Long],
  skippedChildren: List[String],
  errorChildren:   List[String])

case class WorkRecordDTO(
  id:       String,
  status:   WorkStatus,
  tags:     JsObject,
  time:     Option[Long],
  started:  Option[String],
  finished: Option[String])

object WorkRecordDTO {
  implicit val writes = Json.writes[WorkRecordDTO]
}

object WorkTreeRecordDTO {
  implicit val writes = Json.writes[WorkTreeRecordDTO]
}