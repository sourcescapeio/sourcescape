package workers

import services._
import models._
import javax.inject._
import models.index._
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
  savedQueryService:     SavedQueryService,
  elasticSearchService: ElasticSearchService
  )(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  /**
   * Linking
   * TODO: having this decoupled from indexer is super dangerous
   */
  def runLinker(item: IndexerQueueItem, fakeData: Map[String, String])(implicit context: SpanContext) = {
    val orgId = item.orgId
    val repo = item.repo
    val repoId = item.repoId
    val indexId = item.indexId
    val key = models.RepoSHAHelpers.esKey(orgId, repo, repoId, indexId)

    val javascriptSymbolIndex = IndexType.Javascript.symbolIndexName(indexId)
    val javascriptLookupIndex = IndexType.Javascript.lookupIndexName(indexId)
    // elasticSearchService.source(javascriptLookupIndex, Json.obj)

    for {
      index <- repoIndexDataService.getIndexId(indexId).map(_.getOrElse(throw new Exception("invalid index")))
      _ <- socketService.linkingProgress(orgId, repo, repoId, indexId, 0)
      rootIndex <- withDefined(index.rootIndexId) { rootIndexId =>
        repoIndexDataService.getIndexId(rootIndexId)
      }      
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
      symbolCount <- elasticSearchService.count(javascriptSymbolIndex, ESQuery.matchAll)
      (lookupCount, lookupSource) <- elasticSearchService.source(
        javascriptLookupIndex,
        ESQuery.matchAll,
        List("id" -> "asc"),
        100        
      )
      _ = println(lookupCount)
      _ = println(symbolCount)      
      _ <- lookupSource.map { l =>
        (l \ "_source").as[GraphLookup]
      }.via {
        // TODO: danger zone
        indexerService.reportProgress(lookupCount.toInt) { progress =>
          socketService.linkingProgress(orgId, repo, repoId, indexId, progress)
        }
      }.mapAsyncUnordered(4) { fromLookup =>
        for {
          (defs, typeDefs, resp) <- staticAnalysisService.languageServerRequest(
            AnalysisType.ESPrimaTypescript,
            indexId,
            fromLookup.path,
            fromLookup.index
          )
          defEdges <- Source(defs).mapAsync(1) { symbolLookup =>
            withDefined(fromLookup.definitionLink) { definitionLink =>
              for {
                symbolLookupResult <- elasticSearchService.get(javascriptSymbolIndex, symbolLookup.key)
              } yield {
                symbolLookupResult.map { l =>
                  val toSymbol = (l \ "_source").as[GraphSymbol]                  
                  GraphEdge(
                    key,
                    definitionLink,
                    fromLookup.node_id,
                    toSymbol.node_id,
                    Hashing.uuid(),
                    None,
                    None,
                    None)
                }
              }
            }
          }.mapConcat(i => i).runWith(Sinks.ListAccum[GraphEdge])
          typeDefEdges <- Source(typeDefs).mapAsync(1) { symbolLookup =>
            withDefined(fromLookup.typeDefinitionLink) { typeDefinitionLink =>
              for {
                symbolLookupResult <- elasticSearchService.get(javascriptSymbolIndex, symbolLookup.key)
              } yield {
                symbolLookupResult.map { l =>
                  val toSymbol = (l \ "_source").as[GraphSymbol]                   
                  GraphEdge(
                    key,
                    typeDefinitionLink,
                    fromLookup.node_id,
                    toSymbol.node_id,
                    Hashing.uuid(),
                    None,
                    None,
                    None)
                }
              }
            }
          }.mapConcat(i => i).runWith(Sinks.ListAccum[GraphEdge])
        } yield {
          defEdges ++ typeDefEdges
        }
      }.mapConcat(i => i).groupedWithin(100, 100.millis).mapAsync(1) { documents =>
        elasticSearchService.indexBulk(IndexType.Javascript.edgeIndexName, documents.map(i => Json.toJson(i)))
      }.runWith(Sink.ignore)
      _ <- staticAnalysisService.stopLanguageServer(AnalysisType.ESPrimaTypescript, indexId)
      _ <- socketService.linkingProgress(orgId, repo, repoId, indexId, 100)
    } yield {
      ()
    }
  }

  /**
   * Indexing
   */
  def runIndex(item: IndexerQueueItem)(implicit context: SpanContext) = {
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
      /**
       * Generate Symbol tables
       */
      javascriptSymbolIndex = IndexType.Javascript.symbolIndexName(indexId)
      javascriptLookupIndex = IndexType.Javascript.lookupIndexName(indexId)
      _ <- elasticSearchService.dropIndex(javascriptSymbolIndex)
      _ <- elasticSearchService.dropIndex(javascriptLookupIndex)
      _ <- elasticSearchService.ensureIndex(javascriptSymbolIndex, GraphSymbol.mappings)
      _ <- elasticSearchService.ensureIndex(javascriptLookupIndex, GraphLookup.mappings)
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
        .via {
          indexerService.reportProgress(fileTree.length) { progress =>
            socketService.indexingProgress(orgId, repo, repoId, indexId, progress)
          }
        } // report earlier for better accuracy
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
        .via(fanoutIndexing(orgId, repo, repoId, sha, indexId)(materializeSpan))
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
      _ <- socketService.indexingFinished(orgId, repo, repoId, sha, indexId)
    } yield {
      ()
    }
  }

  /**
   * Sub-tasks
   */
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

  private def fanoutIndexing(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int)(context: SpanContext) = {
    indexerService.fanoutIndexing(
      materializeNodes(orgId, repo, repoId, sha, indexId)(context),
      materializeEdges(orgId, repo, repoId, sha)(context),
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

  private def materializeNodes(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int)(context: SpanContext) = {
    Flow[(IndexType, AnalysisTree, GraphResult)].map {
      case (indexType, tree, graphResult) => {
        val raw = graphResult.nodes.map { nb =>
          val graphNode = nb.build(orgId, repo, repoId, sha, tree.indexId, tree.file)
          (nb, graphNode)
        }

        val symbols = raw.flatMap {
          case (nb, nn) => {
            withFlag(nb.generateSymbol) {
              val symbol = GraphSymbol.fromNode(nn)
              Option {
                Some(symbol.key) -> Json.toJson(symbol).as[JsObject]
              }
            }
          }
        }

        val graphNodes = raw.map {
          case (nb, nn) => {
            None -> Json.toJson(nn).as[JsObject]
          }
        }

        val lookups = raw.flatMap {
          case (nb, nn) => {
            // define link type
            withDefined(nb.lookupIndex) { lookupIndex =>
              withFlag(nb.definitionLink.isDefined || nb.typeDefinitionLink.isDefined) {
                val lookup = GraphLookup.fromNode(nn, lookupIndex, nb.definitionLink, nb.typeDefinitionLink)
                Option {
                  None -> Json.toJson(lookup).as[JsObject]
                }
              }
            }
          }
        }

        ifNonEmpty(graphNodes) {
          context.event(s"Indexing nodes for ${tree.file}/${indexType.identifier}. Total: ${graphNodes.length}.")
        }
        ifNonEmpty(symbols) {
          context.event(s"Indexing symbols for ${tree.file}/${indexType.identifier}. Total: ${symbols.length}.")
        }
        ifNonEmpty(lookups) {
          context.event(s"Indexing lookups for ${tree.file}/${indexType.identifier}. Total: ${lookups.length}.")
        }

        List(
          (indexType.nodeIndexName, graphNodes),
          (indexType.symbolIndexName(indexId), symbols),
          (indexType.lookupIndexName(indexId), lookups)
        )
      }
    }.mapConcat(identity)
  }
}
