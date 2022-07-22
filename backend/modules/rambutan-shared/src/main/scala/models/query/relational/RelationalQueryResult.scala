package models.query

import play.api.libs.json._
import akka.stream.scaladsl.{ Source, SourceQueueWithComplete }

case class RelationalQueryResult(
  sizeEstimate:   Long,
  progressSource: Source[Long, Any],
  isDiff:         Boolean,
  columns:        List[QueryColumnDefinition],
  // ordering:       List[String],
  source:  Source[Map[String, JsValue], Any],
  explain: RelationalQueryExplain) {

  def completeExplain = {
    explain.sourceQueue.foreach(_.complete())
  }

  def header = QueryResultHeader(isDiff, columns, sizeEstimate)

}
