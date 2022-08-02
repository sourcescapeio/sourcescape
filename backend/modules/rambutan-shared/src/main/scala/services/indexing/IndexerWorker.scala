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

// for jgit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.NullOutputStream
import org.eclipse.jgit.diff.{ DiffFormatter, DiffEntry }
import org.eclipse.jgit.treewalk.CanonicalTreeParser

//
import scala.jdk.CollectionConverters._
import akka.util.ByteString
import org.joda.time._

@Singleton
class IndexerWorker @Inject() (
  configuration:          play.api.Configuration,
  indexService:           IndexService,
  indexerService:         IndexerService,
  repoIndexDataService:   RepoIndexDataService,
  repoDataService:        RepoDataService,
  fileService:            FileService,
  staticAnalysisService:  StaticAnalysisService,
  queueManagementService: QueueManagementService,
  logService:             LogService,
  socketService:          SocketService,
  indexerQueueService:    IndexerQueueService,
  savedQueryService:      SavedQueryService,
  cachingQueueService:    CachingQueueService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val IndexerConcurrency = 4

  def startRepoIndexing() = {
    // shutdown everything
    for {
      // get repos
      _ <- indexerQueueService.clearQueue()
      _ <- queueManagementService.runQueue[IndexerQueueItem](
        "index-repo",
        concurrency = IndexerConcurrency,
        source = indexerQueueService.source) { item =>
          println("DEQUEUE", item.indexId)
          runIndex(item)
        } { item =>
          println("COMPLETE", item.indexId)
          Future.successful(())
        }
    } yield {
      ()
    }
  }

  def consumeOne() = {
    for {
      item <- indexerQueueService.source.runWith(Sink.head)
      _ = println(item)
      _ <- runIndex(item)
      _ <- indexerQueueService.ack(item) // Don't ack?
    } yield {
      ()
    }
  }

  /**
   * Indexing. Split into separate service.
   */
  private def runIndex(item: IndexerQueueItem) = {
    val orgId = item.orgId
    val repo = item.repo
    val repoId = item.repoId
    val sha = item.sha
    val indexId = item.indexId
    for {
      // hydrate first
      parentRecord <- {
        logService.getRecord(item.workRecordId).map(_.getOrElse(throw new Exception("invalid record")))
      }
      indexRecord <- {
        logService.getRecord(item.indexRecordId).map(_.getOrElse(throw new Exception("invalid record")))
      }
      additionalOrgIds <- repoDataService.getAdditionalOrgs(repoId)
      index <- repoIndexDataService.getIndexId(indexId).map(_.getOrElse(throw new Exception("invalid index")))
      _ <- socketService.indexingProgress(orgId, additionalOrgIds, indexRecord.id, repo, indexId, 0)
      /**
       * Child records
       */
      analysisRecord <- logService.createChild(indexRecord, Json.obj("task" -> "analysis"))
      materializeRecord <- logService.createChild(indexRecord, Json.obj("task" -> "materialize"))
      writeRecord <- logService.createChild(indexRecord, Json.obj("task" -> "write"))
      // cacheRecord <- logService.createChild(indexRecord, Json.obj("task" -> "cache", "indexId" -> indexId, "repo" -> repo))
      allRecords = List(
        indexRecord,
        analysisRecord,
        materializeRecord,
        writeRecord)
      _ <- logService.startRecords(allRecords)
      /**
       * Analysis pipeline
       */
      fileTree = item.paths
      collectionsDirectory = index.collectionsDirectory
      analysisDirectoryBase = index.analysisDirectory
      _ <- logService.event(s"Starting indexing.")(indexRecord)
      _ <- repoIndexDataService.deleteAnalysisTrees(indexId)
      _ <- logService.event(s"Indexing ${fileTree.size} files")(indexRecord)
      /**
       * Index pipeline
       */
      _ <- Source(fileTree)
        .via(reportProgress(orgId, additionalOrgIds, repo, indexId, fileTree.length)(indexRecord)) // report earlier for better accuracy
        .via(readFiles(collectionsDirectory, indexId, concurrency = fileService.parallelism)(indexRecord))
        .via(runAnalysis(analysisDirectoryBase, concurrency = 4)(analysisRecord)) // limited by primadonna
        // .via(writeAnalysisFiles(analysisDirectoryBase, concurrency = 1)(analysisRecord))
        // careful with batchSize, because we'll hold analysis JSONs in memory on backpressure (groupedWithin)
        .via(writeAnalysisTrees(concurrency = 1, batchSize = 10)(analysisRecord))
        .mapConcat {
          case (tree, originalContent, analyzedContent) => {
            // convert analysis type to IndexType
            IndexType.all.filter(_.analysisTypes.contains(tree.analysisType)).map(_ -> (tree, originalContent, analyzedContent))
          }
        }
        .via(getGraph(concurrency = 4)(materializeRecord)) // cpu bound
        .via(fanoutIndexing(orgId, repo, repoId, sha)(materializeRecord))
        .via(indexerService.writeElasticSearch(concurrency = (IndexType.all.length * 2))(writeRecord))
        .runWith(Sink.ignore)
      /**
       * Update pointer
       */
      _ <- logService.event(s"Finished indexing")(indexRecord)
      _ <- logService.event(s"Setting latest SHA for ${repo} to ${sha}")(parentRecord)
      _ <- logService.finishRecords(allRecords)
      /**
       * Finish up
       */
      _ <- logService.finishRecords(parentRecord :: Nil)
      _ <- socketService.indexingFinished(orgId, additionalOrgIds, indexRecord.id, repo, repoId, sha, indexId, parentRecord.id)
    } yield {
      ()
    }
  }

  private def fakeReadFiles(indexId: Int) = {
    Flow[(String, String)].mapConcat {
      case (path, data) => {
        val validAnalysis = AnalysisType.all.filter(_.isValidBlob(path))
        val bytes = ByteString(data)
        validAnalysis.map { at =>
          val tree = AnalysisTree(indexId, path, at)
          (tree, bytes)
        }
      }
    }
  }

  def runTestIndexing(index: RepoSHAIndex, fileTree: Map[String, String]): Future[Unit] = {
    val repo = index.repoName
    val repoId = index.repoId
    val sha = index.sha
    val orgId = index.orgId
    val indexId = index.id
    val analysisDirectoryBase = "" // we may need to mock up grabbing compiled analysis
    for {
      // TODO: create realistic log record
      parentRecord <- {
        logService.createParent(orgId, Json.obj())
      }
      indexRecord = parentRecord // should be child of parentRecord
      analysisRecord = indexRecord // should be child of indexRecord
      materializeRecord = indexRecord // should be child of indexRecord
      writeRecord = indexRecord // should be child of indexRecord
      _ <- Source(fileTree)
        .via(reportProgress(orgId, Nil, repo, indexId, fileTree.size)(indexRecord))
        .via(fakeReadFiles(indexId))
        // may need to mock differently for compiled stuff (Scala)
        .via(runAnalysis(analysisDirectoryBase, concurrency = 4)(analysisRecord)) // limited by primadonna
        // skip
        // .via(writeAnalysisTrees(concurrency = 1, batchSize = 10)(analysisRecord))
        .mapConcat {
          case (tree, originalContent, analyzedContent) => {
            // convert analysis type to IndexType
            IndexType.all.filter(_.analysisTypes.contains(tree.analysisType)).map(_ -> (tree, originalContent, analyzedContent))
          }
        }
        .via(getGraph(concurrency = 4)(materializeRecord)) // cpu bound
        .via(fanoutIndexing(orgId, repo, repoId, sha)(materializeRecord))
        .via(indexerService.writeElasticSearch(concurrency = (IndexType.all.length * 2))(writeRecord))
        .runWith(Sink.ignore)
    } yield {
      ()
    }
  }

  /**
   * Sub-tasks
   */
  private def reportProgress[T](orgId: Int, additionalOrgIds: List[Int], repo: String, indexId: Int, fileTotal: Int)(record: WorkRecord) = {
    indexerService.reportProgress[T](fileTotal) { progress =>
      socketService.indexingProgress(orgId, additionalOrgIds, record.id, repo, indexId, progress)
    }
  }

  private def readFiles(collectionsDirectory: String, indexId: Int, concurrency: Int)(record: WorkRecord) = {
    Flow[String].mapAsyncUnordered(concurrency) { path =>
      logService.withRecord(record) { implicit record =>
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
      }
    }.mapConcat(i => i.toList.flatten)
  }

  private def runAnalysis(analysisDirectoryBase: String, concurrency: Int)(record: WorkRecord) = {
    Flow[(AnalysisTree, ByteString)].mapAsyncUnordered(concurrency) {
      case (tree, content) => {
        logService.withRecord(record) { implicit record =>
          for {
            res <- staticAnalysisService.runAnalysis(analysisDirectoryBase, tree, content)
          } yield {
            res.map { analyzed =>
              (tree, content, analyzed)
            }
          }
        }
      }
    }.mapConcat(i => i.toList.flatten)
  }

  private def writeAnalysisFiles(analysisDirectoryBase: String, concurrency: Int)(record: WorkRecord) = {
    Flow[(AnalysisTree, ByteString, ByteString)].mapAsyncUnordered(concurrency) {
      case (tree, originalContent, analyzedContent) => {
        logService.withRecord(record) { implicit record =>
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
    }.mapConcat(i => i.toList)
  }

  private def writeAnalysisTrees(concurrency: Int, batchSize: Int)(record: WorkRecord) = {
    Flow[(AnalysisTree, ByteString, ByteString)].groupedWithin(batchSize, 100.milliseconds).mapAsyncUnordered(concurrency) {
      case trees => {
        // TODO: do we want to capture errors here?
        repoIndexDataService.writeAnalysisTrees(trees.map(_._1).toList).map(_ => trees)
      }
    }.mapConcat(i => i)
  }

  // private def writeAnalysis

  private def getGraph(concurrency: Int)(record: WorkRecord) = {
    Flow[(IndexType, (AnalysisTree, ByteString, ByteString))].mapAsyncUnordered(concurrency) {
      case (indexType, (tree, originalContent, analyzedContent)) => {
        logService.withRecord(record) { implicit record =>
          val logQueue = Source.queue[(CodeRange, String)](1000, OverflowStrategy.backpressure)
            .groupedWithin(100, 10.milliseconds)
            .mapAsync(1) { grouped =>
              val messages = grouped.map {
                case (range, msg) => s"[${tree.file} ${range.displayString}] ${msg}"
              }
              logService.eventBatch(messages, isError = true)(record)
            }
            .toMat(Sink.ignore)(Keep.left)
            .run()
          val res = indexType.indexer(tree.file, originalContent, analyzedContent, logQueue)
          logQueue.complete()
          Future.successful((indexType, tree, res))
        }
      }
    }.mapConcat(i => i.toList)
  }

  private def fanoutIndexing(orgId: Int, repo: String, repoId: Int, sha: String)(record: WorkRecord) = {
    indexerService.fanoutIndexing(
      materializeNodes(orgId, repo, repoId, sha)(record),
      materializeEdges(orgId, repo, repoId, sha)(record))
  }

  private def materializeEdges(orgId: Int, repo: String, repoId: Int, sha: String)(record: WorkRecord) = {
    Flow[(IndexType, AnalysisTree, GraphResult)].mapAsync(1) {
      case (indexType, tree, graphResult) => {
        logService.withRecord(record) { implicit record =>
          val edges = graphResult.edges.map(_.build(orgId, repo, repoId, tree.indexId, tree.file))
          val (goodEdges, badEdges) = edges.partition {
            case e if e.from =?= "" => false
            case e if e.to =?= ""   => false
            case _                  => true
          }

          for {
            _ <- logService.event(s"Indexing edges for ${tree.file}/${indexType.identifier}. Total: ${edges.length}. Good: ${goodEdges.length}")
            _ <- ifNonEmpty(badEdges) {
              logService.event(s"Bad edges: ${badEdges}")
            }
          } yield {
            val goodEdgesToIndex = goodEdges.map { edge =>
              None -> Json.toJson(edge).as[JsObject]
            }
            (indexType.edgeIndexName, goodEdgesToIndex)
          }
        }
      }
    }.mapConcat(_.toList)
  }

  private def materializeNodes(orgId: Int, repo: String, repoId: Int, sha: String)(record: WorkRecord) = {
    Flow[(IndexType, AnalysisTree, GraphResult)].mapAsync(1) {
      case (indexType, tree, graphResult) => {
        logService.withRecord(record) { implicit record =>
          val nodes = graphResult.nodes.map { node =>
            None -> Json.toJson(node.build(orgId, repo, repoId, sha, tree.indexId, tree.file)).as[JsObject]
          }
          for {
            _ <- logService.event(s"Indexing nodes for ${tree.file}/${indexType.identifier}. Total: ${nodes.length}.")
          } yield {
            (indexType.nodeIndexName, nodes)
          }
        }
      }
    }.mapConcat(_.toList)
  }
}
