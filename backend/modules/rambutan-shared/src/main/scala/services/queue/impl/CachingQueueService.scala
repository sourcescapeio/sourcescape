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

case class CachingQueueItem(
  orgId:        Int,
  cacheId:      Int,
  workId:       String,
  existingKeys: List[String],
  indexIds:     List[Int])

object CachingQueueItem {
  implicit val format = Json.format[CachingQueueItem]
}

/**
 * Definition
 */
private object CachingQueueDefinition extends IdempotentQueue[CachingQueueItem]("caching") {
  def objectToKey(obj: CachingQueueItem): String = {
    // cacheId is often reused when re-caching so we go by workId
    // honestly, workId is always a solid call for idempotent key
    obj.workId.toString()
  }
}

@Singleton
class CachingQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  /**
   * Open methods
   */
  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(CachingQueueDefinition)
  }

  def enqueue(item: CachingQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(CachingQueueDefinition, item)
  }

  def source: Source[CachingQueueItem, Any] = {
    idempotentQueueService.source(CachingQueueDefinition)
  }
}
