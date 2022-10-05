package controllers

import models._
import javax.inject._
import silvousplay.imports._
import silvousplay.api._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.collection.immutable.Seq
import akka.stream.scaladsl.{ Source, Sink, Keep }
import akka.stream.OverflowStrategy
import play.api.libs.json._
import akka.util.ByteString
import models.index.GraphNode
import models.index.GraphEdge

@Singleton
class IndexController @Inject() (
  configuration:         play.api.Configuration,
  telemetryService:      TelemetryService,
  consumerService:       services.ConsumerService,
  repoService:           services.RepoService,
  repoDataService:       services.RepoDataService,
  repoIndexDataService:  services.RepoIndexDataService,
  logService:            services.LogService,
  indexService:          services.IndexService,
  indexerQueueService:   services.IndexerQueueService,
  socketService:         services.SocketService,
  staticAnalysisService: services.StaticAnalysisService,
  authService:           services.AuthService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def getTreeForIndex(orgId: Int, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        repoService.getIndexTree(indexId).map(_.map(_.dto))
      }
    }
  }

  def getFile(orgId: Int, indexId: Int, file: String) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        Future.successful(())
      }
    }
  }

  import akka.stream.scaladsl.FileIO
  import java.nio.file.Paths
  def getAnalysis(orgId: Int, indexId: Int, file: String) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        for {
          index <- repoIndexDataService.getIndexId(indexId).map {
            _.getOrElse(throw models.Errors.notFound("index.dne", "Index not found"))
          }
          // analysisPath = AnalysisType.ScalaSemanticDB.path(
          //   index.analysisDirectory,
          //   file)
          // analysisBytes <- FileIO.fromPath(Paths.get(analysisPath + ".semanticdb")).runWith(Sinks.ByteAccum)
          // analysisFile = scala.io.Source.fromFile(path).getLines().mkString("\n")
        } yield {
          Json.obj("file" -> "")
        }
      }
    }
  }

  def deleteAllIndexes(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        for {
          indexes <- repoIndexDataService.getIndexesForOrg(orgId)
          _ <- Source(indexes).mapAsync(4) { idx =>
            repoService.deleteSHAIndex(orgId, idx.repoId, idx.id)
          }.runWith(Sink.ignore)
        } yield {
          ()
        }
      }
    }
  }

  def runIndexForSHA(orgId: Int, repoId: Int, sha: String, forceRoot: Boolean) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        telemetryService.withTelemetry { implicit c =>
          for {
            repo <- repoDataService.getRepo(repoId).map {
              _.getOrElse(throw models.Errors.notFound("repo.dne", "Repo not found"))
            }
            _ <- consumerService.runCleanIndexForSHA(orgId, repo.repoName, repoId, sha, forceRoot)
          } yield {
            ()
          }
        }
      }
    }
  }

  def deleteIndexForSHA(orgId: Int, repoId: Int, sha: String, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        repoService.deleteSHAIndex(orgId, repoId, indexId)
      }
    }
  }

  import services.IndexerQueueItem
  def reindex(orgId: Int, repoId: Int, sha: String, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        // We only use this to debug so only going to do clean ones
        for {
          index <- repoIndexDataService.getIndexId(indexId).map {
            _.getOrElse(throw models.Errors.notFound("index.dne", "Index not found"))
          }
          // create records
          parentRecord <- logService.createParent(orgId, Json.obj("repoId" -> repoId, "sha" -> sha))
          indexRecord <- logService.createChild(parentRecord, Json.obj("task" -> "indexing"))
          // get trees
          paths <- repoIndexDataService.getSHAIndexTreeBatch(List(indexId)).map(_.getOrElse(indexId, Nil))
          // delete
          // no need to delete trees as will be wiped by indexer
          _ <- indexService.deleteKey(index)
          // index
          item = IndexerQueueItem(
            index.orgId,
            index.repoName,
            index.repoId,
            index.sha,
            index.id,
            paths,
            parentRecord.id,
            indexRecord.id)
          _ <- indexerQueueService.ack(item)
          _ <- indexerQueueService.enqueue(item)
        } yield {
          ()
        }
      }
    }
  }

  def testIndex(orgId: Int, indexType: IndexType, languageServer: Boolean) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withJson { forms: List[TestIndexForm] =>
          val indexId = 0

          val analysisType = indexType.analysisTypes.head

          val contentMap = forms.map { f =>
            f.file -> f.content
          }.toMap

          for {
            _ <- withFlag(languageServer) {
              staticAnalysisService.startLanguageServer(analysisType, indexId, contentMap)
            }
            items <- Source(forms).mapAsync(4) { form =>

              val fakeTree = AnalysisTree(0, "", analysisType)

              val content = ByteString(form.content)
              for {
                resp <- staticAnalysisService.runAnalysis(
                  "",
                  fakeTree,
                  content).map(_.getOrElse(ByteString.empty))
                (nodes, edges) <- Future {
                  val logQueue = Source.queue[(CodeRange, String)](10, OverflowStrategy.dropBuffer)
                    .map { item =>
                      println(item)
                    }
                    .toMat(Sink.ignore)(Keep.left)
                    .run()
                  val graph = indexType.indexer(form.file, content, resp, logQueue)
                  logQueue.complete()
                  val renderedNodes = graph.nodes.map { node =>
                    node.build(orgId, "repo", 0, "null-sha", 0, form.file) -> node.lookupRange
                  }
                  val renderedEdges = graph.edges.map(_.build(orgId, "repo", 0, 0, form.file))

                  // Select nodes to emit

                  (renderedNodes, renderedEdges)
                }.recover {
                  case err => {
                    val traceLines = err.getStackTrace().map { i =>
                      s"${i.getFileName()}:${i.getLineNumber()}"
                    }

                    println(s"Uncaught Exception: ${err.getMessage()}")
                    traceLines.foreach(println)
                    (Nil, Nil)
                  }
                }
              } yield {
                (
                  form.file,
                  indexType.prettyPrint(content, resp),
                  nodes,
                  edges)
              }
            }.runWith(Sinks.ListAccum[(String, String, List[(GraphNode, Option[CodeRange])], List[GraphEdge])])
            // send a few nodes
            collectedNodes = items.foldLeft(List.empty[(GraphNode, Option[CodeRange])])(_ ++ _._3)
            allNodes = collectedNodes.map(_._1)
            allEdges = items.foldLeft(List.empty[GraphEdge])(_ ++ _._4)
            // get links
            symbolTable = allNodes.map { n =>
              "a" -> "b"
            }.toMap
            allLinks <- withFlag(languageServer) {
              Source(collectedNodes).mapAsync(4) {
                case (n, Some(range)) => {
                  staticAnalysisService.languageServerRequest(analysisType, indexId, n.path, range.startIndex) map { resp =>
                    Option(Json.obj(
                      "original" -> n,
                      "response" -> resp))
                  }
                }
                case _ => {
                  Future.successful(None)
                }
              }.mapConcat(identity).runWith(Sinks.ListAccum[JsValue])
            }
            // stop
            _ <- withFlag(languageServer) {
              staticAnalysisService.stopLanguageServer(analysisType, indexId)
            }
          } yield {
            Json.obj(
              "analysis" -> items.map(i => {
                Json.obj(
                  "file" -> i._1,
                  "analysis" -> i._2)
              }),
              "links" -> allLinks,
              "nodes" -> allNodes.map(i => Json.toJson(i)),
              "edges" -> allEdges.map(i => Json.toJson(i)))
          }
        }
      }
    }
  }
}
