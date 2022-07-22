package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Sink }

case class SnapshotterQueueItem(orgId: Int, schemaId: Int, indexId: Int, workRecordId: String)

object SnapshotterQueueItem {
  implicit val format = Json.format[SnapshotterQueueItem]
}

/**
 * Definition
 */
private object SnapshotterQueueDefinition extends IdempotentQueue[SnapshotterQueueItem]("snapshot") {
  def objectToKey(obj: SnapshotterQueueItem): String = {
    s"${obj.schemaId}/${obj.indexId}"
  }
}

@Singleton
class SnapshotterQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext) {

  /**
   * Open methods
   */
  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(SnapshotterQueueDefinition)
  }

  def enqueue(item: SnapshotterQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(SnapshotterQueueDefinition, item)
  }

  def ack(item: SnapshotterQueueItem): Future[Unit] = {
    idempotentQueueService.ack(SnapshotterQueueDefinition, List(item))
  }

  def source: Source[SnapshotterQueueItem, Any] = {
    idempotentQueueService.source(SnapshotterQueueDefinition)
  }
}
