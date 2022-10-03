package test

import services._
import workers._
import models.{ RepoSHAIndex, IndexType, RepoSHAHelpers }
import models.query._
import models.graph._

import org.scalatestplus.play.guice._
import dal.SharedDataAccessLayer
import scala.concurrent.{ ExecutionContext, Future }
import akka.util.ByteString
import play.api.libs.json._
import models.Schema
import org.mockito.MockitoSugar

trait IndexHelpers {
  self: RambutanSpec =>

  protected def runTestIndex(index: RepoSHAIndex, indexType: IndexType)(data: (String, String)*)(implicit ec: ExecutionContext) = {
    val dal = app.injector.instanceOf[SharedDataAccessLayer]
    val indexerWorker = app.injector.instanceOf[IndexerWorker]
    val elasticSearchService = app.injector.instanceOf[ElasticSearchService]
    val fileService = app.injector.instanceOf[FileService]

    // mock up file service calls
    data.foreach {
      case (path, v) => {
        val fullPath = s"${RepoSHAHelpers.CollectionsDirectory}/${index.esKey}/${path}"
        when(fileService.readFile(fullPath)).thenReturn {
          Future.successful(ByteString(v))
        }
      }
    }

    for {
      _ <- dal.RepoSHAIndexTable.insert(index)
      _ <- indexerWorker.runTestIndexing(
        index,
        data.toMap)
      // force refresh to get data to propagate
      _ <- elasticSearchService.refresh(indexType.nodeIndexName)
      _ <- elasticSearchService.refresh(indexType.edgeIndexName)
    } yield {
      ()
    }
  }

  // protected def createSchema(title: String, index: RepoSHAIndex, indexType: IndexType, fieldAliases: Map[String, String] = Map.empty[String, String])(q: String*)(selected: String*)(implicit ec: ExecutionContext) = {
  //   val schemaService = app.injector.instanceOf[SchemaService]

  //   for {
  //     schema <- schemaService.createSchema(
  //       index.orgId,
  //       title = title,
  //       fieldAliases = fieldAliases,
  //       context = SrcLogCodeQuery.parseOrDie(q.mkString("\n"), indexType).dto,
  //       selected = selected.toList,
  //       named = Nil,
  //       fileFilter = None,
  //       selectedRepos = List(index.repoId))
  //   } yield {
  //     schema.schema
  //   }
  // }

  // protected def runSnapshot(index: RepoSHAIndex, schema: Schema)(implicit ec: ExecutionContext) = {
  //   val logService = app.injector.instanceOf[LogService]
  //   val snapshotter = app.injector.instanceOf[SnapshotterWorker]

  //   for {
  //     workRecord <- logService.createParent(index.orgId, Json.obj("test" -> "test"))
  //     queueItem = SnapshotterQueueItem(index.orgId, schema.id, index.id, workRecord.id)
  //     _ <- snapshotter.runSnapshot(queueItem)
  //     // force refresh
  //     // _ <- refreshGenericIndexes()
  //   } yield {
  //     ()
  //   }
  // }

  // protected def refreshGenericIndexes()(implicit ec: ExecutionContext) = {
  //   val elasticSearchService = app.injector.instanceOf[ElasticSearchService]

  //   for {
  //     _ <- elasticSearchService.refresh(GenericGraphNode.globalIndex)
  //     _ <- elasticSearchService.refresh(GenericGraphEdge.globalIndex)
  //   } yield {
  //     ()
  //   }
  // }
}
