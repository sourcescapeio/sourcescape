package services

import models._
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.Source
import play.api.libs.json._
import javax.inject._

case class CompilerQueueItem(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int, paths: List[String], compilations: List[CompilerType], workRecordId: String, compilerRecordId: String, indexRecordId: String) {
  def toIndexer = IndexerQueueItem(orgId, repo, repoId, sha, indexId, paths, workRecordId, indexRecordId)
}

object CompilerQueueItem {
  implicit val format = Json.format[CompilerQueueItem]
}

/**
 * Definition
 */
private object CompilerQueueDefinition extends IdempotentQueue[CompilerQueueItem]("compiler") {
  def objectToKey(obj: CompilerQueueItem): String = {
    // will we recompile indexes?
    // should we just be using the work id?
    obj.indexId.toString()
  }
}

@Singleton
class CompilerQueueService @Inject() (
  configuration:          play.api.Configuration,
  idempotentQueueService: IdempotentQueueService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  /**
   * Open methods
   */
  def clearQueue(): Future[Unit] = {
    idempotentQueueService.clearQueue(CompilerQueueDefinition)
  }

  def enqueue(item: CompilerQueueItem): Future[Unit] = {
    // add item to individual queue
    idempotentQueueService.enqueue(CompilerQueueDefinition, item)
  }

  def source: Source[CompilerQueueItem, Any] = {
    idempotentQueueService.source(CompilerQueueDefinition)
  }
}
