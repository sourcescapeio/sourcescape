package models.index

import play.api.libs.json._

case class GraphEdge(
  key:    String,
  `type`: String, // should be restricted
  from:   String,
  to:     String,
  id:     String,
  toType: Option[String],
  name:   Option[String],
  index:  Option[Int])

object GraphEdge {

  val BaseMappings = Json.obj(
    "dynamic" -> false,
    "properties" -> Json.obj(
      "key" -> Json.obj(
        "type" -> "keyword"),
      "type" -> Json.obj(
        "type" -> "keyword"),
      "from" -> Json.obj(
        "type" -> "keyword"),
      "to" -> Json.obj(
        "type" -> "keyword"),
      "id" -> Json.obj(
        "type" -> "keyword"),
      // only used for contains type hints (forward only)
      "toType" -> Json.obj(
        "type" -> "keyword"),
      // optionals
      "name" -> Json.obj(
        "type" -> "keyword"),
      "index" -> Json.obj(
        "type" -> "integer")))

  val mappings = Json.obj(
    "settings" -> Json.obj(
      "index" -> Json.obj(
        "sort.field" -> List("key", "from", "id"),
        "sort.order" -> List("asc", "asc", "asc"))),
    "mappings" -> BaseMappings)

  // We don't use reads. Manually reading the JSON
  implicit val writes = Json.writes[GraphEdge]
}
