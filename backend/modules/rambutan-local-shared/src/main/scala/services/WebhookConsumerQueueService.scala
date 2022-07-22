package services

import models._
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.libs.json._
import akka.stream.scaladsl.Source

// work item
case class WebhookConsumerQueueItem(orgId: Int, repoId: Int)

object WebhookConsumerQueueItem {
  implicit val format = Json.format[WebhookConsumerQueueItem]
}

/**
 * Definition
 */
private object WebhookConsumerQueueDefinition extends IdempotentQueue[WebhookConsumerQueueItem]("webhook_consumer") {
  def objectToKey(obj: WebhookConsumerQueueItem): String = {
    // we go by repo id to block massive numbers of webhooks
    obj.repoId.toString()
  }
}

@Singleton
class WebhookConsumerQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  /**
   * Actions
   */

  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(WebhookConsumerQueueDefinition)
  }

  def enqueue(item: WebhookConsumerQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(WebhookConsumerQueueDefinition, item)
  }

  def ack(item: WebhookConsumerQueueItem): Future[Unit] = {
    idempotentQueueService.ack(WebhookConsumerQueueDefinition, List(item))
  }

  def source: Source[WebhookConsumerQueueItem, Any] = {
    idempotentQueueService.source(WebhookConsumerQueueDefinition)
  }
}

