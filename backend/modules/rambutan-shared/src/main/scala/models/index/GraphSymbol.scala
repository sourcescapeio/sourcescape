package models.index

import play.api.libs.json._
import silvousplay.imports._

// TODO: this is Typescript specific. Should we standardize?
case class SymbolLookup(
  file: String,
  startIndex: Int,
  endIndex: Int,
  // debug
  kind: String,
  name: String,
  containerName: String
) {
  def key = Keyable.encode(s"${file}:${startIndex}:${endIndex}")
}

object SymbolLookup {

  implicit val writes = Json.writes[SymbolLookup]

  def parse(d: JsValue) = {
    val start = (d \ "contextSpan" \ "start").asOpt[Int].getOrElse {
      (d \ "textSpan" \ "start").as[Int]
    }
    val length = (d \ "contextSpan" \ "length").asOpt[Int].getOrElse {
      (d \ "textSpan" \ "start").as[Int]
    }
    SymbolLookup(
      (d \ "fileName").as[String],
      start,
      start + length,
      (d \ "kind").as[String],
      (d \ "name").as[String],
      (d \ "containerName").as[String],
    )
  }
}

case class GraphSymbol(
  path:        String,
  start_index: Int,
  end_index:   Int,
  node_id:     String) {

  // TODO: should we hash this to distribute it?
  def key = s"/${path}:${start_index}:${end_index}"

}

object GraphSymbol {
  def fromNode(node: GraphNode) = {
    GraphSymbol(node.path, node.start_index, node.end_index, node.id)
  }

  val mappings = Json.obj(
    "mappings" -> Json.obj(
      "dynamic" -> false,
      "properties" -> Json.obj(
        "path" -> Json.obj(
          "enabled" -> false),
        "start_index" -> Json.obj(
          "enabled" -> false),
        "end_index" -> Json.obj(
          "enabled" -> false),
        "node_id" -> Json.obj(
          "enabled" -> false))))

  implicit val format = Json.format[GraphSymbol]
}

case class GraphLookup(
  id:      String,
  node_id: String,
  path:    String,
  index: Int,
  definitionLink: Option[String],
  typeDefinitionLink: Option[String]
)

object GraphLookup {
  def fromNode(node: GraphNode, lookupIndex: Int, definitionLink: Option[String], typeDefinitionLink: Option[String]) = {
    GraphLookup(Hashing.uuid(), node.id, node.path, lookupIndex, definitionLink, typeDefinitionLink)
  }

  val mappings = Json.obj(
    "settings" -> Json.obj(
      "index" -> Json.obj(
        "sort.field" -> List("id"),
        "sort.order" -> List("asc"))),
    "mappings" -> Json.obj(
      "dynamic" -> false,
      "properties" -> Json.obj(
        "id" -> Json.obj(
          "type" -> "keyword"),
        "node_id" -> Json.obj(
          "enabled" -> false),
        "path" -> Json.obj(
          "enabled" -> false),
        "index" -> Json.obj(
          "enabled" -> false))))

  implicit val format = Json.format[GraphLookup]
}
