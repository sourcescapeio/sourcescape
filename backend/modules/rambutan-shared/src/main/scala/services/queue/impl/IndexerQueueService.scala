package services

import models._
import models.index.{ GraphNode, GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Sink }

case class IndexerQueueItem(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int, paths: List[String], workRecordId: String, indexRecordId: String)

object IndexerQueueItem {
  implicit val format = Json.format[IndexerQueueItem]
}

/**
 * Definition
 */
private object IndexerQueueDefinition extends IdempotentQueue[IndexerQueueItem]("indexer") {
  def objectToKey(obj: IndexerQueueItem): String = {
    obj.indexId.toString()
  }
}

@Singleton
class IndexerQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext) {

  /**
   * Open methods
   */
  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(IndexerQueueDefinition)
  }

  def enqueue(item: IndexerQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(IndexerQueueDefinition, item)
  }

  def ack(item: IndexerQueueItem): Future[Unit] = {
    idempotentQueueService.ack(IndexerQueueDefinition, List(item))
  }

  def source: Source[IndexerQueueItem, Any] = {
    idempotentQueueService.source(IndexerQueueDefinition)
  }
}
