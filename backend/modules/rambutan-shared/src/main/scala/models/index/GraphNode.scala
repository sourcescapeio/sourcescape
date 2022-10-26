package models.index

import models.query.FileKey
import play.api.libs.json._
import models.graph.GenericGraphProperty

// NOTE: we can deserialize this as GraphNode
case class GraphNode(
  id: String,
  // targeting keys
  repo: String, // not indexed
  sha:  String, // not indexed
  key:  String, // orgId, repo, repoId, indexId
  path: String,
  // node keys
  `type`:       String, // should be restricted
  start_line:   Int,
  end_line:     Int,
  start_column: Int,
  end_column:   Int,
  //
  start_index: Int,
  end_index:   Int,
  // optionals
  name:        Option[String],
  search_name: List[String],
  props:       List[GenericGraphProperty],
  tags:        List[String],
  index:       Option[Int]) {

  def fileKey = FileKey(key, path)
}

object GraphNode {
  val NameLimit = 1000

  // shard by (type, key)
  val mappings = Json.obj(
    "settings" -> Json.obj(
      "index" -> Json.obj(
        "sort.field" -> List("key", "path", "id"),
        "sort.order" -> List("asc", "asc", "asc"))),
    "mappings" -> Json.obj(
      "dynamic" -> false,
      "properties" -> Json.obj(
        "id" -> Json.obj(
          "type" -> "keyword"),
        "repo" -> Json.obj(
          // "type" -> "keyword"
          "enabled" -> false),
        "sha" -> Json.obj(
          // "type" -> "keyword",
          "enabled" -> false),
        "key" -> Json.obj(
          "type" -> "keyword"),
        "path" -> Json.obj(
          "type" -> "keyword"),
        "type" -> Json.obj(
          "type" -> "keyword"),
        // should these be searchable?
        "start_line" -> Json.obj(
          "type" -> "integer"),
        "end_line" -> Json.obj(
          "type" -> "integer"),
        "start_column" -> Json.obj(
          "type" -> "integer"),
        "end_column" -> Json.obj(
          "type" -> "integer"),
        // optionals
        "name" -> Json.obj(
          "enabled" -> false), // this is strictly for display
        "search_name" -> Json.obj(
          "type" -> "text",
          "analyzer" -> "keyword"), // better analyzer here?
        "props" -> Json.obj(
          "type" -> "keyword"),
        "tags" -> Json.obj(
          "type" -> "keyword"),
        "index" -> Json.obj(
          "type" -> "integer"))))

  implicit val format = Json.format[GraphNode]
}
