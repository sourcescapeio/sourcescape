package test

import services._
import workers._
import models.{ RepoSHAIndex, IndexType, RepoSHAHelpers }
import models.query._
import models.graph._
import models.index._

import org.scalatestplus.play.guice._
import dal.SharedDataAccessLayer
import scala.concurrent.{ ExecutionContext, Future }
import akka.util.ByteString
import play.api.libs.json._
import org.mockito.MockitoSugar
import silvousplay.api.NoopSpanContext

trait IndexHelpers {
  self: RambutanSpec =>

  private val Key = "key"
  private val Repo = "repo"
  private val SHA = "sha"
  private val Path = "path"

  protected case class Node(id: String, `type`: String, name: Option[String] = None, index: Option[Int] = None, tags: List[String] = Nil) {
    def toGraph = GraphNode(id, Repo, SHA, Key, Path, `type`, 0, 0, 0, 0, 0, 0, name, name.toList, tags, index)
  }

  protected case class Edge(id: String, `type`: String, from: String, to: String, name: Option[String] = None, index: Option[Int] = None) {
    def toGraph = GraphEdge(Key, `type`, from, to, id, None, name, index)
  }

  protected def runGraphIndex(indexType: IndexType)(
    nodes: Node*)(
    edges: Edge*)(implicit ec: ExecutionContext) = {

    val elasticSearchService = app.injector.instanceOf[ElasticSearchService]

    for {
      _ <- elasticSearchService.indexBulk(indexType.nodeIndexName, nodes.map(_.toGraph).map(n => Json.toJson(n)))
      _ <- elasticSearchService.indexBulk(indexType.edgeIndexName, edges.map(_.toGraph).map(n => Json.toJson(n)))
    } yield {
      ()
    }
  }

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
      indexId = queueItem.indexId
      _ <- indexerWorker.runIndex(queueItem)(NoopSpanContext)
      javascriptSymbolIndex = IndexType.Javascript.symbolIndexName(indexId)
      javascriptLookupIndex = IndexType.Javascript.lookupIndexName(indexId)
      _ <- elasticSearchService.refresh(javascriptSymbolIndex)
      _ <- elasticSearchService.refresh(javascriptLookupIndex)
      _ = println("FINISHED INDEXING")

      _ <- indexerWorker.runLinker(queueItem, data.toMap)(NoopSpanContext)
      // force refresh to get data to propagate
      _ <- elasticSearchService.dropIndex(javascriptSymbolIndex)
      _ <- elasticSearchService.dropIndex(javascriptLookupIndex)
      _ <- elasticSearchService.refresh(indexType.nodeIndexName)
      _ <- elasticSearchService.refresh(indexType.edgeIndexName)
    } yield {
      ()
    }
  }
}
