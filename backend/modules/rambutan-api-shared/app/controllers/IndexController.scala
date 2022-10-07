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
import models.index.{ GraphNode, GraphEdge, GraphNodeBuilder }

@Singleton
class IndexController @Inject() (
  configuration:         play.api.Configuration,
  telemetryService:      TelemetryService,
  repoService:           services.RepoService,
  repoDataService:       services.RepoDataService,
  repoIndexDataService:  services.RepoIndexDataService,
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

  def testIndex(orgId: Int, indexType: IndexType, languageServer: Boolean) = {
    api(parse.tolerantJson) { implicit request =>
      telemetryService.withTelemetry { implicit context =>
        withJson { forms: List[TestIndexForm] =>
          val IndexId = 0
          val RepoId = 0
          val RepoName = "repo"
          val Sha = "null-sha"

          val analysisType = indexType.analysisTypes.head

          val contentMap = forms.map { f =>
            f.file -> f.content
          }.toMap

          for {
            _ <- withFlag(languageServer) {
              staticAnalysisService.startInMemoryLanguageServer(analysisType, IndexId, contentMap)
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
                  val graph = indexType.indexer(form.file, content, resp, context)
                  val renderedNodes = graph.nodes.map { node =>
                    (
                      node.build(orgId, RepoName, RepoId, Sha, IndexId, form.file),
                      node)
                  }
                  val renderedEdges = graph.edges.map(_.build(orgId, RepoName, RepoId, IndexId, form.file))

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
            }.runWith(Sinks.ListAccum[(String, String, List[(GraphNode, GraphNodeBuilder)], List[GraphEdge])])
            // send a few nodes
            collectedNodes = items.foldLeft(List.empty[(GraphNode, GraphNodeBuilder)])(_ ++ _._3)
            allNodes = collectedNodes.map(_._1)
            // get links
            symbolTable = collectedNodes.flatMap {
              case (nn, nb) if nb.symbolLookup => {
                Option(s"/${nn.path}:${nn.start_index}:${nn.end_index}" -> nn)
              }
              case _ => None
            }.toMap
            allLinks <- withFlag(languageServer) {
              Source(collectedNodes).mapAsync(4) {
                case (n, nb) => {
                  withDefined(nb.lookupIndex) { lookupIndex =>
                    for {
                      (defs, typeDefs, resp) <- staticAnalysisService.languageServerRequest(analysisType, IndexId, n.path, lookupIndex)
                      defResult = defs.flatMap { d =>
                        symbolTable.get(d.key)
                      }
                      typeResult = typeDefs.flatMap { d =>
                        symbolTable.get(d.key)
                      }
                    } yield {
                      // create edges
                      val defEdges = defResult.flatMap { d =>
                        nb.definitionLink(orgId, RepoName, RepoId, IndexId, n.path)(d)
                      }

                      val typeEdges = typeResult.flatMap { dt =>
                        // n.typeLink
                        nb.typeDefinitionLink(orgId, RepoName, RepoId, IndexId, n.path)(dt)
                      }

                      val edges = defEdges ++ typeEdges

                      Option(edges -> Json.obj(
                        "original" -> n,
                        "edges" -> edges,
                        "resp" -> resp,
                        // "defLookup" -> defs,
                        // "typeLookup" -> typeDefs,
                        "def" -> defResult,
                        "types" -> typeResult))
                    }
                  }
                }
                case _ => {
                  Future.successful(None)
                }
              }.mapConcat(identity).runWith(Sinks.ListAccum[(List[GraphEdge], JsValue)])
            }
            // stop
            _ <- withFlag(languageServer) {
              staticAnalysisService.stopLanguageServer(analysisType, IndexId)
            }
            linkedEdges = allLinks.flatMap(_._1)
            linkDebugInfo = allLinks.map(_._2)
            allEdges = items.foldLeft(List.empty[GraphEdge])(_ ++ _._4) ++ linkedEdges
          } yield {
            Json.obj(
              "analysis" -> items.map(i => {
                Json.obj(
                  "file" -> i._1,
                  "analysis" -> i._2)
              }),
              "links" -> Json.obj(
                "symbols" -> symbolTable,
                "links" -> linkDebugInfo),
              "nodes" -> allNodes.map(i => Json.toJson(i)),
              "edges" -> allEdges.map(i => Json.toJson(i)))
          }
        }
      }
    }
  }
}
