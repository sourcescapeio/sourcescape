package models.query

import silvousplay.imports._
import models.{ CodeRange, CodeLocation }
import models.index.{ GraphNode }
import play.api.libs.json._

case class NearbyCode(
  code:       String,
  startLine:  Int,
  endLine:    Int,
  startIndex: Int)

object NearbyCode {
  implicit val writes = Json.writes[NearbyCode]
}

case class QueryNode(
  id:          String,
  key:         String,
  repo:        String,
  path:        String,
  sha:         String,
  name:        Option[String],
  search_name: List[String],
  range:       CodeRange,
  extracted:   String,
  nearby:      Option[NearbyCode],
  checksum:    String,
  edgeType:    String,
  `type`:      String,
  truncated:   Boolean) {
  def keyItem = RelationalKeyItem(key, path, id)
}

object QueryNode {
  val MaxSize = 10000
  val MaxExtracted = 400

  def extract(node: GraphNode, edgeType: String, text: String) = {
    val range = CodeRange(
      start = CodeLocation(node.start_line, node.start_column),
      end = CodeLocation(node.end_line, node.end_column),
      startIndex = node.start_index,
      endIndex = node.end_index)

    val (nearby, truncated) = if (range.size > MaxSize) {
      (None, true)
    } else {
      val nearbyEnd = node.end_line + 2
      val nearbyStart = math.max(1, node.start_line - 2)
      val (nearbyIdx, nearbyCode) = CodeRange.applyRangeByLine(text, nearbyStart, nearbyEnd, node.start_line, node.end_line)
      if (nearbyCode.length > MaxSize) {
        (None, true)
      } else {

        val nearby = NearbyCode(
          nearbyCode,
          nearbyStart,
          nearbyEnd,
          nearbyIdx + node.start_column)
        (Some(nearby), false)
      }
    }

    val extracted = CodeRange.applyRange(text, range.startIndex, range.endIndex)
    val checksum = Hashing.checksum(extracted.getBytes())

    QueryNode(
      node.id,
      node.key,
      node.repo,
      node.path,
      node.sha,
      node.name,
      node.search_name,
      range,
      extracted.take(MaxExtracted),
      nearby,
      checksum,
      edgeType,
      node.`type`,
      truncated)
  }

  implicit val writes = Json.writes[QueryNode]
}