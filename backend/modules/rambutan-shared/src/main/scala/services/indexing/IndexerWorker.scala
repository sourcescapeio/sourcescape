package workers

import services._
import models._
import javax.inject._
import models.index.{ GraphEdge, GraphResult }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow, Sink, Keep, GraphDSL, Merge, Broadcast, FileIO }
import akka.stream.OverflowStrategy
import akka.util.ByteString
import silvousplay.api.SpanContext
import scala.util.Success
import scala.util.Try
import scala.util.Failure

@Singleton
class IndexerWorker @Inject() (
  configuration:         play.api.Configuration,
  indexService:          IndexService,
  indexerService:        IndexerService,
  repoIndexDataService:  RepoIndexDataService,
  repoDataService:       RepoDataService,
  fileService:           FileService,
  staticAnalysisService: StaticAnalysisService,
  socketService:         SocketService,
  indexerQueueService:   IndexerQueueService,
  savedQueryService:     SavedQueryService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  /**
   * Indexing. Split into separate service.
   */
  def runIndex(item: IndexerQueueItem, fakeData: Map[String, String])(implicit context: SpanContext) = {
    val orgId = item.orgId
    val repo = item.repo
    val repoId = item.repoId
    val sha = item.sha
    val indexId = item.indexId
    for {
      index <- repoIndexDataService.getIndexId(indexId).map(_.getOrElse(throw new Exception("invalid index")))
      _ <- socketService.indexingProgress(orgId, repo, repoId, indexId, 0)
      rootIndex <- withDefined(index.rootIndexId) { rootIndexId =>
        repoIndexDataService.getIndexId(rootIndexId)
      }
      /**
       * Start up servers
       */
      fileTree = item.paths
      collectionsDirectory = index.collectionsDirectory
      _ <- if(fakeData.isEmpty) {
        staticAnalysisService.startDirectoryLanguageServer(
          AnalysisType.ESPrimaTypescript,
          indexId,
          List(
            Option {
              fileService.realPath(index.collectionsDirectory)
            },
            rootIndex.map { idx =>
              fileService.realPath(idx.collectionsDirectory)
            }
          ).flatten
        )
      } else {
        staticAnalysisService.startInMemoryLanguageServer(
          AnalysisType.ESPrimaTypescript,
          indexId,
          fakeData          
        )
      }
      // TODO: fix hardcode
      analysisDirectoryBase = index.analysisDirectory
      _ = context.event("Starting indexing.")
      _ <- repoIndexDataService.deleteAnalysisTrees(indexId)
      _ = context.event(s"Indexing ${fileTree.size} files")
      analysisSpan = context.decoupledSpan("Analysis")
      materializeSpan = context.decoupledSpan("Materialize")
      writeSpan = context.decoupledSpan("Writes")
      /**
       * Index pipeline
       */
      _ <- Source(fileTree)
        .via(reportProgress(orgId, repo, repoId, indexId, fileTree.length)) // report earlier for better accuracy
        .via(readFiles(collectionsDirectory, indexId, concurrency = 1)(context))
        .via(runAnalysis(analysisDirectoryBase, concurrency = 4)(analysisSpan)) // limited by primadonna
        // .via(writeAnalysisFiles(analysisDirectoryBase, concurrency = 1)(analysisRecord))
        // careful with batchSize, because we'll hold analysis JSONs in memory on backpressure (groupedWithin)
        .via(writeAnalysisTrees(concurrency = 1, batchSize = 10)(analysisSpan))
        .mapConcat {
          case (tree, originalContent, analyzedContent) => {
            // convert analysis type to IndexType
            IndexType.all.filter(_.analysisTypes.contains(tree.analysisType)).map(_ -> (tree, originalContent, analyzedContent))
          }
        }
        .via(getGraph(concurrency = 4)(materializeSpan)) // cpu bound
        .via(fanoutIndexing(orgId, repo, repoId, sha)(materializeSpan))
        .via(indexerService.writeElasticSearch(concurrency = (IndexType.all.length * 2))(writeSpan))
        .runWith(Sink.ignore)
      //
      _ = analysisSpan.terminate()
      _ = materializeSpan.terminate()
      _ = writeSpan.terminate()
      /**
       * Update pointer
       */
      _ = context.event("Finished indexing")
      _ = context.event(s"Setting latest SHA for ${repo} to ${sha}")
      /**
       * Finish up
       */
      // TODO: fix hardcode
      _ <- staticAnalysisService.stopLanguageServer(AnalysisType.ESPrimaTypescript, indexId)
      _ <- socketService.indexingFinished(orgId, repo, repoId, sha, indexId)
    } yield {
      ()
    }
  }

  /**
   * Sub-tasks
   */
  private def reportProgress[T](orgId: Int, repo: String, repoId: Int, indexId: Int, fileTotal: Int) = {
    indexerService.reportProgress[T](fileTotal) { progress =>
      socketService.indexingProgress(orgId, repo, repoId, indexId, progress)
    }
  }

  private def readFiles(collectionsDirectory: String, indexId: Int, concurrency: Int)(context: SpanContext) = {
    Flow[String].mapAsyncUnordered(concurrency) { path =>
      val sourceFile = s"${collectionsDirectory}/${path}"
      val validAnalysis = AnalysisType.all.filter(_.isValidBlob(sourceFile))
      for {
        bytes <- ifNonEmpty(validAnalysis) {
          fileService.readFile(sourceFile)
        }
      } yield {
        validAnalysis.map { at =>
          val tree = AnalysisTree(indexId, path, at)
          (tree, bytes)
        }
      }
    }.mapConcat(identity)
  }

  private def runAnalysis(analysisDirectoryBase: String, concurrency: Int)(context: SpanContext) = {
    Flow[(AnalysisTree, ByteString)].mapAsyncUnordered(concurrency) {
      case (tree, content) => {
        for {
          res <- staticAnalysisService.runAnalysis(analysisDirectoryBase, tree, content)
        } yield {
          res.map { analyzed =>
            (tree, content, analyzed)
          }
        }
      }
    }.mapConcat(identity)
  }

  private def writeAnalysisFiles(analysisDirectoryBase: String, concurrency: Int)(context: SpanContext) = {
    Flow[(AnalysisTree, ByteString, ByteString)].mapAsyncUnordered(concurrency) {
      case (tree, originalContent, analyzedContent) => {
        val path = tree.analysisPath(analysisDirectoryBase)
        for {
          // highly inefficient
          // tree.analysisType.shouldWrite
          _ <- fileService.writeFile(path, analyzedContent)
        } yield {
          (tree, originalContent, analyzedContent)
        }
      }
    }
  }

  private def writeAnalysisTrees(concurrency: Int, batchSize: Int)(context: SpanContext) = {
    Flow[(AnalysisTree, ByteString, ByteString)].groupedWithin(batchSize, 100.milliseconds).mapAsyncUnordered(concurrency) {
      case trees => {
        // TODO: do we want to capture errors here?
        repoIndexDataService.writeAnalysisTrees(trees.map(_._1).toList).map(_ => trees)
      }
    }.mapConcat(i => i)
  }

  // private def writeAnalysis

  private def getGraph(concurrency: Int)(context: SpanContext) = {
    Flow[(IndexType, (AnalysisTree, ByteString, ByteString))].mapAsyncUnordered(concurrency) {
      case (indexType, (tree, originalContent, analyzedContent)) => {
        Try(indexType.indexer(tree.file, originalContent, analyzedContent, context)) match {
          case Success(res) => Future.successful(Some(indexType, tree, res))
          case Failure(e) => Future.successful {
            context.event("Error while parsing", "e" -> e.getMessage())
            None
          }
        }
      }
    }.mapConcat(identity)
  }

  private def fanoutIndexing(orgId: Int, repo: String, repoId: Int, sha: String)(context: SpanContext) = {
    indexerService.fanoutIndexing(
      materializeNodes(orgId, repo, repoId, sha)(context),
      materializeEdges(orgId, repo, repoId, sha)(context),

      // setup links
      // buildSymbolTable,
      // filter
    )
  }

  private def materializeEdges(orgId: Int, repo: String, repoId: Int, sha: String)(context: SpanContext) = {
    Flow[(IndexType, AnalysisTree, GraphResult)].map {
      case (indexType, tree, graphResult) => {
        val edges = graphResult.edges.map(_.build(orgId, repo, repoId, tree.indexId, tree.file))
        val (goodEdges, badEdges) = edges.partition {
          case e if e.from =?= "" => false
          case e if e.to =?= ""   => false
          case _                  => true
        }

        context.event(s"Indexing edges for ${tree.file}/${indexType.identifier}. Total: ${edges.length}. Good: ${goodEdges.length}")
        ifNonEmpty(badEdges) {
          context.event(s"Bad edges: ${badEdges}")
        }
        val goodEdgesToIndex = goodEdges.map { edge =>
          None -> Json.toJson(edge).as[JsObject]
        }
        (indexType.edgeIndexName, goodEdgesToIndex)
      }
    }
  }

  private def materializeNodes(orgId: Int, repo: String, repoId: Int, sha: String)(context: SpanContext) = {
    Flow[(IndexType, AnalysisTree, GraphResult)].map {
      case (indexType, tree, graphResult) => {
        val nodes = graphResult.nodes.map { node =>
          None -> Json.toJson(node.build(orgId, repo, repoId, sha, tree.indexId, tree.file)).as[JsObject]
        }
        context.event(s"Indexing nodes for ${tree.file}/${indexType.identifier}. Total: ${nodes.length}.")
        (indexType.nodeIndexName, nodes)
      }
    }
  }
}
