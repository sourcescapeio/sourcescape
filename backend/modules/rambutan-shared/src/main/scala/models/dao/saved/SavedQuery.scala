package models

import silvousplay.imports._
import models.query._
import play.api.libs.json._

sealed abstract class SavedQueryStatus(val identifier: String) extends Identifiable

object SavedQueryStatus extends Plenumeration[SavedQueryStatus] {
  case object Pending extends SavedQueryStatus("pending")
  case object Cached extends SavedQueryStatus("cached")
}

case class SavedQuery(
  id:              Int,
  orgId:           Int,
  name:            String,
  language:        IndexType,
  nodes:           JsValue,
  edges:           JsValue,
  aliases:         JsValue,
  defaultSelected: String,
  temporary:       Boolean,
  created:         Long) {

  def selectedQuery: SrcLogCodeQuery = {
    val allQueries = SrcLogOperations.extractComponents(this.toModel).map(_._2)

    allQueries.find(_.vertexes.contains(this.defaultSelected)).orElse {
      allQueries.headOption
    }.getOrElse {
      throw new Exception(s"invalid query selection")
    }
  }

  def toModel = {
    SrcLogCodeQueryDTO(
      language,
      nodes.as[List[NodeClauseDTO]],
      edges.as[List[EdgeClauseDTO]],
      aliases.as[Map[String, String]],
      root = None).toModel
  }

  def dto = {
    val state = BuilderState(
      this.toModel,
      Some(defaultSelected),
      Map.empty[String, Boolean],
      false,
      update = false)

    SavedQueryDTO(id, name, state.dto)
  }
}

case class SavedQueryDTO(
  id:      Int,
  name:    String,
  context: query.BuilderStateDTO)

object SavedQueryDTO {
  implicit val writes = Json.writes[SavedQueryDTO]
}