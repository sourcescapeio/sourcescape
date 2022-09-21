package services

import models._
import models.graph._
import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.libs.json._
import org.joda.time._
import akka.stream.scaladsl.{ Source, Sink }
import silvousplay.api.SpanContext

@Singleton
class SnapshotService @Inject() (
  dao:                     dal.SharedDataAccessLayer,
  configuration:           play.api.Configuration,
  documentationService:    DocumentationService,
  snapshotterQueueService: SnapshotterQueueService,
  srcLogQueryService:      SrcLogQueryService,
  repoIndexDataService:    RepoIndexDataService,
  logService:              LogService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  def getSnapshotsBatch(schemaId: Int, indexIds: List[Int]): Future[Map[Int, Snapshot]] = {
    val keys = indexIds.map(schemaId -> _)
    dao.SnapshotTable.byPK.lookupBatch(keys).map {
      _.flatMap {
        case ((k1, k2), Some(v)) => Some(k2 -> v)
        case _                   => None
      }
    }
  }

  def getSnapshotsForSchema(schemaId: Int): Future[List[HydratedSnapshot]] = {
    for {
      snapshots <- dao.SnapshotTable.bySchema.lookup(schemaId)
      indexes <- repoIndexDataService.getIndexIds(snapshots.map(_.indexId).distinct)
      indexMap = indexes.map { i =>
        i.id -> i
      }.toMap
    } yield {
      snapshots.map { s =>
        val i = indexMap.getOrElse(s.indexId, throw new Exception("invalid index"))
        HydratedSnapshot(s, i)
      }
    }
  }

  def getLatestSnapshot(schema: Schema)(implicit context: SpanContext): Future[Option[HydratedSnapshot]] = {
    implicit val targeting = GenericGraphTargeting(schema.orgId)

    val repoId = schema.selectedRepos.head

    val source = repoIndexDataService.getChain(
      schema.orgId,
      repoId,
      nodes = List(
        NodeClause(GenericGraphNodePredicate.GitHead, "A", Some(
          GraphPropertyCondition(
            List(
              GenericGraphProperty("repo_id", repoId.toString()),
              GenericGraphProperty("head", "master"),
              GenericGraphProperty("head", "develop")))))),
      edges = List(
        EdgeClause(GenericGraphEdgePredicate.GitHeadCommit, "A", "B", None, None)),
      predicate = GenericGraphEdgePredicate.GitCommitParent,
      leading = "B")

    for {
      snaps <- dao.SnapshotTable.bySchema.lookup(schema.id)
      latestSnapshot <- ifNonEmpty(snaps) {
        source.groupedWithin(10, 100.milliseconds).mapAsync(1) { shas =>
          for {
            indexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, shas.map(_.sha).toList)
            indexMap = indexes.groupBy(_.sha)
            snapshots <- getSnapshotsBatch(schema.id, indexes.map(_.id))
          } yield {
            // ordered
            shas.flatMap { sha =>
              // List[T]
              for {
                idx <- indexMap.getOrElse(sha.sha, Nil)
                snapshot <- snapshots.get(idx.id).toList
              } yield {
                snapshot
              }
            }
          }
        }.mapConcat(i => i.toList).runWith(Sink.headOption)
      }
      hydrated <- withDefined(latestSnapshot) { s =>
        repoIndexDataService.getIndexId(s.indexId).map { maybeIndex =>
          withDefined(maybeIndex) { i =>
            Option {
              HydratedSnapshot(
                s,
                maybeIndex.getOrElse(throw new Exception("need index")))
            }
          }
        }
      }
    } yield {
      hydrated
    }
  }

  def upsertSnapshotData(snapshot: Snapshot): Future[Unit] = {
    dao.SnapshotTable.insertOrUpdate(snapshot) map (_ => ())
  }

  def upsertSnapshots(orgId: Int, schemaId: Int, indexIds: List[Int]): Future[Unit] = {
    Source(indexIds).mapAsync(1) { indexId =>
      for {
        record <- logService.createParent(orgId, Json.obj("task" -> "snapshot", "schemaId" -> schemaId, "indexId" -> indexId))
        snapshot = Snapshot(schemaId, indexId, record.id, SnapshotStatus.Pending)
        _ <- dao.SnapshotTable.insertOrUpdate(snapshot)
        queueItem = SnapshotterQueueItem(orgId, snapshot.schemaId, snapshot.indexId, snapshot.workId)
        _ <- snapshotterQueueService.ack(queueItem)
        _ <- snapshotterQueueService.enqueue(queueItem)
      } yield {
        ()
      }
    }.runWith(Sink.ignore) map (_ => ())
  }

  def getSnapshotDataForIndex(orgId: Int, schemaId: Int, indexId: Int)(implicit context: SpanContext): Future[(QueryResultHeader, Source[Map[String, JsValue], Any])] = {
    implicit val targeting = GenericGraphTargeting(orgId)
    val Row = "Row"
    for {
      schema <- dao.SchemaTable.byId.lookup(schemaId).map {
        _.getOrElse(throw Errors.notFound("schema.dne", "Schema does not exist"))
      }
      annotationColumns <- documentationService.getAnnotationColumns(schemaId)
      // snag the columns
      codeEdges = schema.sequence.flatMap { col =>
        val cellName = col + "Cell"
        List(
          EdgeClause(GenericGraphEdgePredicate.SnapshotRowCell, Row, cellName, Some(
            GraphPropertyCondition(
              GenericGraphProperty("column", col) :: Nil)),
            None),
          EdgeClause(GenericGraphEdgePredicate.SnapshotCellData, cellName, col, None, None))
      }
      annotationEdges = annotationColumns.map { col =>
        EdgeClause(GenericGraphEdgePredicate.SnapshotRowAnnotation, Row, col.srcLogName, Some(
          GraphPropertyCondition(
            GenericGraphProperty("column_id", col.id.toString()) :: Nil)), Some(BooleanModifier.Optional))
      }
      source <- srcLogQueryService.runQueryGeneric(SrcLogGenericQuery(
        nodes = List(
          NodeClause(GenericGraphNodePredicate.SnapshotRow, Row, Some(
            GraphPropertyCondition(
              GenericGraphProperty("schema_id", schemaId.toString()) ::
                GenericGraphProperty("index_id", indexId.toString()) :: Nil)))),
        edges = codeEdges ++ annotationEdges,
        root = None,
        selected = Row :: schema.sequence ++ annotationColumns.map(_.srcLogName)))
      // hydrate various annotations here
      hydratedSource = source.groupedWithin(100, 100.milliseconds).mapAsync(1) { items =>
        // toHydrate =
        val documentationIds = items.flatten.map(_._2).flatMap { node =>
          withFlag(node.`type` =?= GenericGraphNodeType.Annotation.identifier) {
            withFlag((node.payload \ "type").asOpt[String] =?= Option("documentation")) {
              (node.payload \ "id").asOpt[Int]
            }
          }
        }.toList

        for {
          docs <- documentationService.getHydratedDocumentationBatch(documentationIds)
        } yield {
          items.map {
            _.map {
              case (k, v) if v.`type` =?= GenericGraphNodeType.Annotation.identifier => {
                val vv = if ((v.payload \ "type").asOpt[String] =?= Option("documentation")) {
                  val maybeDoc = withDefined((v.payload \ "id").asOpt[Int]) { id =>
                    docs.get(id).map(_.dto)
                  }
                  Json.toJson(maybeDoc)
                } else {
                  Json.toJson(v)
                }
                k -> vv
              }
              case (k, v) => {
                k -> Json.toJson(v)
              }
            }
          }
        }
      }.mapConcat(i => i)
      header = QueryResultHeader(
        isDiff = false,
        sizeEstimate = 0L,
        columns = schema.sequence.map { id =>
          QueryColumnDefinition(id, QueryResultType.GenericCode)
        } ++ annotationColumns.map { col =>
          QueryColumnDefinition(col.srcLogName, QueryResultType.DocumentationAnnotation, Json.obj("id" -> col.id))
        })
    } yield {
      (header, hydratedSource)
    }
  }

  // TODO: impl
  def deleteSnapshotForIndex(orgId: Int, schemaId: Int, indexId: Int)(implicit context: SpanContext): Future[Unit] = {
    implicit val targeting = GenericGraphTargeting(orgId)

    // snapshot
    // snapshot rows
    // snapshot cells
    // snapshot

    val A = "A"
    for {
      source <- srcLogQueryService.runQueryGeneric(SrcLogGenericQuery(
        nodes = List(
          NodeClause(GenericGraphNodePredicate.Snapshot, A, None)),
        edges = List(),
        root = None,
        selected = Nil))
      items <- source.runWith(Sinks.ListAccum)
    } yield {
      items.foreach(println)
      ()
    }
  }
}
