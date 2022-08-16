package controllers

import models._
import javax.inject._
import silvousplay.api.API
import silvousplay.imports._
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
import silvousplay.api.Telemetry

@Singleton
class IndexController @Inject() (
  configuration:         play.api.Configuration,
  consumerService:       services.ConsumerService,
  repoService:           services.RepoService,
  repoDataService:       services.RepoDataService,
  repoIndexDataService:  services.RepoIndexDataService,
  logService:            services.LogService,
  indexService:          services.IndexService,
  indexerQueueService:   services.IndexerQueueService,
  socketService:         services.SocketService,
  staticAnalysisService: services.StaticAnalysisService,
  authService:           services.AuthService)(implicit ec: ExecutionContext, as: ActorSystem) extends API with Telemetry {

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
          analysisPath = AnalysisType.ScalaSemanticDB.path(
            index.analysisDirectory,
            file)
          analysisBytes <- FileIO.fromPath(Paths.get(analysisPath + ".semanticdb")).runWith(Sinks.ByteAccum)
          // analysisFile = scala.io.Source.fromFile(path).getLines().mkString("\n")
        } yield {
          Json.obj("file" -> IndexType.Scala.prettyPrintAnalysis(analysisBytes))
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
        withTelemetry { implicit c =>
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

  def testIndex(orgId: Int, indexType: IndexType) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withJson { form: TestIndexForm =>
          val analysisType = indexType.analysisTypes.head
          val fakeTree = AnalysisTree(0, "", analysisType)

          val content = ByteString(form.text)
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
              val graph = indexType.indexer("null", content, resp, logQueue)
              logQueue.complete()
              val renderedNodes = graph.nodes.map(_.build(orgId, "null-repo", 0, "null-sha", 0, "null-path")).map(i => Json.toJson(i))
              val renderedEdges = graph.edges.map(_.build(orgId, "null-repo", 0, 0, "null-path")).map(i => Json.toJson(i))
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
            Json.obj(
              "analysis" -> indexType.prettyPrint(content, resp),
              "nodes" -> nodes,
              "edges" -> edges)
            // Json.obj("analysis" -> Json.parse(resp))
          }
        }
      }
    }
  }
}
