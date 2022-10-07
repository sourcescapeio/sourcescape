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
import silvousplay.api.NoopSpanContext

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
          Future.successful(ByteString(v, "UTF-8"))
        }
      }
    }

    for {
      _ <- dal.RepoSHAIndexTable.insert(index)
      queueItem = IndexerQueueItem(
        index.orgId,
        index.repoName,
        index.repoId,
        index.sha,
        index.id,
        data.map(_._1).toList)
      _ <- indexerWorker.runIndex(queueItem)(NoopSpanContext)
      // force refresh to get data to propagate
      _ <- elasticSearchService.refresh(indexType.nodeIndexName)
      _ <- elasticSearchService.refresh(indexType.edgeIndexName)
    } yield {
      ()
    }
  }
}
