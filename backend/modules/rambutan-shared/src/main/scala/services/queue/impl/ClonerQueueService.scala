package services

import models._
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Sink }

// work item
case class ClonerQueueDirty(modified: Map[String, String], deleted: Set[String]) {
  def addPaths = modified.keySet

  def signature = DirtySignature(modified, deleted.toList)
}

object ClonerQueueDirty {
  implicit val format = Json.format[ClonerQueueDirty]
}

case class ClonerQueueItem(orgId: Int, repoId: Int, indexId: Int, dirtyFiles: Option[ClonerQueueDirty], workId: String) {
  val dirty = dirtyFiles.isDefined
}

object ClonerQueueItem {
  implicit val format = Json.format[ClonerQueueItem]
}

private object ClonerQueueDefinition extends IdempotentQueue[ClonerQueueItem]("cloner") {
  def objectToKey(obj: ClonerQueueItem): String = {
    // could use workId here
    obj.indexId.toString()
  }
}

@Singleton
class ClonerQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  /**
   * Actions
   */
  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(ClonerQueueDefinition)
  }

  def enqueue(item: ClonerQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(ClonerQueueDefinition, item)
  }

  def source: Source[ClonerQueueItem, Any] = {
    idempotentQueueService.source(ClonerQueueDefinition)
  }
}

