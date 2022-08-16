package services

import models.{ IndexType, ESQuery, Errors, Sinks }
import models.index.GraphNode
import silvousplay.TSort
import models.query._
import models.index.esprima._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.duration._
import java.util.Base64
import akka.stream.Attributes
import akka.stream.scaladsl.{ Source, Flow, Sink, GraphDSL, Broadcast, Concat, Merge }
import models.graph.GenericGraphNode

@Singleton
class NodeHydrationService @Inject() (
  configuration:        play.api.Configuration,
  fileService:          FileService,
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val CodeJoinBatchSize = 20 // keep this small. no real gain from batching GCS
  val NodeHydrationBatchSize = 1000

  /**
   * Non-generics
   */

  // def rehydrateGenericNodes(base: Source[GraphTrace[GenericGraphUnit], Any])(implicit targeting: QueryTargeting[GenericGraphUnit]): Source[GraphTrace[GenericGraphNode], Any] = {
  //   joinNodes[GraphTrace[GenericGraphUnit], GraphTrace[GenericGraphNode], GenericGraphUnit](base)
  // }

  /**
   * Partial rehydrates
   */
  def rehydrateNodeMap[TU, IN](base: Source[Map[String, GraphTrace[TU]], Any])(
    implicit
    targeting:   QueryTargeting[TU],
    tracing:     QueryTracing[GraphTrace[TU]],
    hasTraceKey: HasTraceKey[TU],
    mapper:      HydrationMapper[TraceKey, JsObject, Map[String, GraphTrace[TU]], Map[String, GraphTrace[IN]]]): Source[Map[String, GraphTrace[IN]], Any] = {
    joinNodes[Map[String, GraphTrace[TU]], Map[String, GraphTrace[IN]], TU](base)
  }

  def rehydrateCodeMap[IN, NO](base: Source[Map[String, GraphTrace[IN]], Any])(
    implicit
    fileKeyExtractor: FileKeyExtractor[IN],
    mapper:           HydrationMapper[FileKey, String, Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]]): Source[Map[String, GraphTrace[NO]], Any] = {
    type B = Map[String, GraphTrace[IN]]
    type C = Map[String, GraphTrace[NO]]
    joinCode[B, C, IN](base) { remainingKeys =>
      getTextForFiles(remainingKeys)
    }
  }

  /**
   * Full hydrates
   */
  def rehydrate[TU, IN, NO](base: Source[GraphTrace[TU], Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[GraphTrace[TU]],
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    node:             HydrationMapper[TraceKey, JsObject, GraphTrace[TU], GraphTrace[IN]],
    code:             HydrationMapper[FileKey, String, GraphTrace[IN], GraphTrace[NO]]): Source[GraphTrace[NO], Any] = {
    type A = GraphTrace[TU]
    type B = GraphTrace[IN]
    type C = GraphTrace[NO]

    rehydrateInternal[A, B, C, TU, IN, NO](base)
  }

  def rehydrateMap[TU, IN, NO](base: Source[Map[String, GraphTrace[TU]], Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[GraphTrace[TU]],
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, GraphTrace[TU]], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, String, Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]]): Source[Map[String, GraphTrace[NO]], Any] = {
    type A = Map[String, GraphTrace[TU]]
    type B = Map[String, GraphTrace[IN]]
    type C = Map[String, GraphTrace[NO]]

    rehydrateInternal[A, B, C, TU, IN, NO](base)
  }

  private def rehydrateInternal[A, B, C, TU, IN, NO](base: Source[A, Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[GraphTrace[TU]],
    hasTraceKey:      HasTraceKey[TU],
    nodeFlattener:    HydrationFlattener[A, TU],
    codeFlattener:    HydrationFlattener[B, IN],
    fileKeyExtractor: FileKeyExtractor[IN],
    node:             HydrationMapper[TraceKey, JsObject, A, B],
    code:             HydrationMapper[FileKey, String, B, C]): Source[C, Any] = {
    val withNode = joinNodes[A, B, TU](base)
    joinCode[B, C, IN](withNode) { remainingKeys =>
      getTextForFiles(remainingKeys)
    }
  }

  /**
   * Base joins
   */
  private def joinNodes[From, To, TU](base: Source[From, Any])(
    implicit
    targeting:       QueryTargeting[TU],
    tracing:         QueryTracing[GraphTrace[TU]],
    traceExtraction: HasTraceKey[TU],
    flattener:       HydrationFlattener[From, TU],
    mapper:          HydrationMapper[TraceKey, JsObject, From, To]): Source[To, _] = {
    base.groupedWithin(NodeHydrationBatchSize, 100.milliseconds).mapAsync(1) { traces =>
      val allTraces = flattener.flatten(traces.toList)
      for {
        (total, source) <- elasticSearchService.source(
          targeting.nodeIndexName,
          ESQuery.bool(
            filter = targeting.nodeQuery(allTraces) :: Nil),
          sort = targeting.nodeSort,
          scrollSize = NodeHydrationBatchSize)
        nodeMap <- source.runWith(Sink.fold(Map.empty[TraceKey, JsObject]) {
          case (acc, item) => {
            val key = traceExtraction.traceKey(tracing.unitFromJs(item).terminusId)
            val obj = (item \ "_source").as[JsObject]
            acc + (key -> obj)
          }
        })
      } yield {
        traces.map { trace =>
          mapper.hydrate(trace, nodeMap)
        }
      }
    }.mapConcat {
      case s => s
    }
  }

  private def joinCode[From, To, IN](in: Source[From, Any])(f: List[FileKey] => Future[Map[FileKey, String]])(
    implicit
    flattener:        HydrationFlattener[From, IN],
    fileKeyExtractor: FileKeyExtractor[IN],
    mapper:           HydrationMapper[FileKey, String, From, To]): Source[To, Any] = {
    val codeScanInitial = (Map.empty[FileKey, String], List.empty[To])
    in.groupedWithin(CodeJoinBatchSize, 100.milliseconds).scanAsync(codeScanInitial) {
      case ((codeMap, _), next) => {
        val keys = flattener.flatten(next.toList).flatMap(fileKeyExtractor.extract).distinct

        val (hasKeys, remainingKeys) = keys.partition(k => codeMap.contains(k))

        for {
          lookup <- ifNonEmpty(remainingKeys) {
            f(remainingKeys.toList)
          }
          fullMap = (codeMap ++ lookup)
          nextJoined = next.map { n =>
            mapper.hydrate(n, fullMap)
          }
        } yield {
          // println("CODEMAP" + fullMap.size)
          (fullMap, nextJoined.toList)
        }
      }
    }.mapConcat(_._2)
  }

  /**
   * Helper
   */
  private def getTextForFiles(keys: List[FileKey]): Future[Map[FileKey, String]] = {
    for {
      res <- Source(keys).mapAsync(1) { key =>
        val fullPath = s"${models.RepoSHAHelpers.CollectionsDirectory}/${key.key}/${key.path}"
        fileService.readFile(fullPath).map { data =>
          key -> data
        }
      }.runWith(Sink.fold(Map.empty[FileKey, String]) {
        case (acc, (k, d)) => acc + (k -> d.utf8String)
      })
    } yield {
      res.toMap
    }
  }
}