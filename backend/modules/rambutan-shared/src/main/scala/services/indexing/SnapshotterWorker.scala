package workers

import services._
import models._
import models.query._
import models.graph._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import silvousplay.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow, Sink, Keep, GraphDSL, Merge, Broadcast }
import akka.stream.OverflowStrategy
import org.joda.time.DateTime
import models.graph.snapshot._

@Singleton
class SnapshotterWorker @Inject() (
  configuration:       play.api.Configuration,
  queueManagementService:  QueueManagementService,
  logService:              LogService,
  telemetryService: TelemetryService,
  socketService:           SocketService,
  snapshotterQueueService: SnapshotterQueueService,
  // data
  repoIndexDataService:  RepoIndexDataService,
  indexerService:        IndexerService,
  schemaService:         SchemaService,
  snapshotService:       SnapshotService,
  savedQueryDataService: SavedQueryDataService,
  // query
  queryTargetingService:  QueryTargetingService,
  srcLogCompilerService:  SrcLogCompilerService,
  relationalQueryService: RelationalQueryService,
)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val SnapshotterConcurrency = 2

  def startSnapshotter() = {
    // shutdown everything
    for {
      // get repos
      _ <- snapshotterQueueService.clearQueue()
      _ <- queueManagementService.runQueue[SnapshotterQueueItem](
        "snapshot",
        concurrency = SnapshotterConcurrency,
        source = snapshotterQueueService.source) { item =>
          println("DEQUEUE", item)
          runSnapshot(item)
        } { item =>
          println("COMPLETE", item)
          Future.successful(())
        }
    } yield {
      ()
    }
  }

  def runSnapshot(item: SnapshotterQueueItem) = {
    val orgId = item.orgId

    telemetryService.withTelemetry { implicit context =>
      for {
        // pull all assets
        // _ <- item.indexId
        schema <- schemaService.getSchema(item.schemaId).map {
          _.getOrElse(throw new Exception("invalid schema"))
        }
        parentRecord <- logService.getRecord(item.workRecordId).map {
          _.getOrElse(throw new Exception("invalid work record"))
        }
        savedQuery <- savedQueryDataService.getSavedQuery(orgId, schema.savedQueryId).map {
          _.getOrElse(throw new Exception("invalid schema: saved query"))
        }
        index <- repoIndexDataService.getIndexId(item.indexId).map {
          _.getOrElse(throw new Exception("invalid index for snapshot"))
        }
        snapshot = Snapshot(item.schemaId, item.indexId, item.workRecordId, SnapshotStatus.InProgress)
        _ <- snapshotService.upsertSnapshotData(snapshot)
        // TODO: default selection mismatch
        // We may need to restrict selecting across multiple queries
        selectedQuery = savedQuery.selectedQuery
        // resolve appropriate targeting
        targeting <- queryTargetingService.resolveTargeting(
          orgId,
          selectedQuery.language,
          QueryTargetingRequest.ForIndexes(List(item.indexId), schema.fileFilter))
        builderQuery <- srcLogCompilerService.compileQuery(selectedQuery)(targeting)
        count <- {
          val countQuery = builderQuery.copy(select = RelationalSelect.CountAll)
          for {
            countResult <- relationalQueryService.runQuery(
              countQuery,
              explain = false,
              progressUpdates = false)(targeting, context, QueryScroll(None))
            countJson <- countResult.source.runWith(Sink.last).map { i =>
              Json.toJson(i)
            }
          } yield {
            (countJson \ "*" \ "count").as[Int]
          }
        }
        // write out schema
        groupedQuery = builderQuery.applyDistinct(schema.selected, schema.named)
        result <- relationalQueryService.runQuery(
          groupedQuery,
          explain = false,
          progressUpdates = false)(targeting, context, QueryScroll(None))
        totalItems = result.sizeEstimate
        // snapshot node
        (snapshotExpression, schemaColumns) = {
          SnapshotWriter.materializeSnapshot(schema, index)
        }
        snapshotNode = snapshotExpression.node
        source <- result.source
          .via(indexerService.reportProgress(count) { progress =>
            println(progress)
          })
          .map { data =>
            SnapshotWriter.materializeRow(data, snapshotNode, schemaColumns)
          }.concat(Source(snapshotExpression :: Nil))
          .via(indexerService.wrapperFlow(orgId, parentRecord))
          .runWith(Sink.ignore)
      } yield {
        ()
      }
    }
  }
}
