package services

import models.{ IndexType, ESQuery, Errors, Sinks }
import models.index.GraphNode
import silvousplay.TSort
import models.query._
import models.index.esprima._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import silvousplay.api._
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

  val CodeJoinBatchSize = 200 // keep this small. no real gain from batching GCS
  val CodeJoinLatency = 200.milliseconds

  val NodeHydrationBatchSize = 1000
  val NodeHydrationLatency = 200.milliseconds

  /**
   * Non-generics
   */

  // def rehydrateGenericNodes(base: Source[GraphTrace[GenericGraphUnit], Any])(implicit targeting: QueryTargeting[GenericGraphUnit]): Source[GraphTrace[GenericGraphNode], Any] = {
  //   joinNodes[GraphTrace[GenericGraphUnit], GraphTrace[GenericGraphNode], GenericGraphUnit](base)
  // }

  /**
   * Partial rehydrates
   */
  def rehydrateNodeMap[T, TU, IN](base: Source[Map[String, T], Any])(
    implicit
    targeting:   QueryTargeting[TU],
    tracing:     QueryTracingBasic[TU],
    context:     SpanContext,
    hasTraceKey: HasTraceKey[TU],
    flattener:   HydrationFlattener[Map[String, T], TU],
    mapper:      HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]]): Source[Map[String, GraphTrace[IN]], Any] = {
    joinNodes[Map[String, T], Map[String, GraphTrace[IN]], TU](base)
  }

  def rehydrateCodeMap[IN, NO](base: Source[Map[String, GraphTrace[IN]], Any])(
    implicit
    context:          SpanContext,
    fileKeyExtractor: FileKeyExtractor[IN],
    mapper:           HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]]): Source[Map[String, GraphTrace[NO]], Any] = {
    type B = Map[String, GraphTrace[IN]]
    type C = Map[String, GraphTrace[NO]]
    joinCode[B, C, IN](base) { remainingKeys =>
      getTextForFiles(remainingKeys)
    }
  }

  /**
   * Full hydrates
   */
  def rehydrate[T, TU, IN, NO](base: Source[T, Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracingBasic[TU],
    context:          SpanContext,
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    flattener:        HydrationFlattener[T, TU],
    node:             HydrationMapper[TraceKey, JsObject, T, GraphTrace[IN]],
    code:             HydrationMapper[FileKey, (String, Array[String]), GraphTrace[IN], GraphTrace[NO]]): Source[GraphTrace[NO], Any] = {
    type A = GraphTrace[TU]
    type B = GraphTrace[IN]
    type C = GraphTrace[NO]

    rehydrateInternal[T, B, C, TU, IN, NO](base)
  }

  def rehydrateMap[T, TU, IN, NO](base: Source[Map[String, T], Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracingBasic[TU],
    context:          SpanContext,
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]]): Source[Map[String, GraphTrace[NO]], Any] = {
    type A = Map[String, T]
    type B = Map[String, GraphTrace[IN]]
    type C = Map[String, GraphTrace[NO]]

    rehydrateInternal[A, B, C, TU, IN, NO](base)
  }

  private def rehydrateInternal[A, B, C, TU, IN, NO](base: Source[A, Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracingBasic[TU],
    context:          SpanContext,
    hasTraceKey:      HasTraceKey[TU],
    nodeFlattener:    HydrationFlattener[A, TU],
    codeFlattener:    HydrationFlattener[B, IN],
    fileKeyExtractor: FileKeyExtractor[IN],
    node:             HydrationMapper[TraceKey, JsObject, A, B],
    code:             HydrationMapper[FileKey, (String, Array[String]), B, C]): Source[C, Any] = {
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
    tracing:         QueryTracingBasic[TU],
    context:         SpanContext,
    traceExtraction: HasTraceKey[TU],
    flattener:       HydrationFlattener[From, TU],
    mapper:          HydrationMapper[TraceKey, JsObject, From, To]): Source[To, _] = {
    context.withSpanS("query.rehydrate.nodes") { cc =>
      base.groupedWithin(NodeHydrationBatchSize, NodeHydrationLatency).mapAsync(1) { traces =>

        val allTraces = flattener.flatten(traces.toList)
        // traces.foreach(println)
        for {
          (total, source) <- cc.withSpan(
            "query.rehydrate.nodes.query",
            "size" -> traces.length.toString()) { cc2 =>
              for {
                (total, source) <- elasticSearchService.source(
                  targeting.nodeIndexName,
                  ESQuery.bool(
                    filter = targeting.nodeQuery(allTraces) :: Nil),
                  sort = targeting.nodeSort,
                  scrollSize = NodeHydrationBatchSize)
              } yield {
                (total, cc2.withSpanS("query.rehydrate.nodes.consume") { _ =>
                  source
                })
              }
            }
          nodeMap <- source.runWith(Sink.fold(Map.empty[TraceKey, JsObject]) {
            case (acc, item) => {
              val key = traceExtraction.traceKey(tracing.unitFromJs(item))
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
  }

  private def joinCode[From, To, IN](in: Source[From, Any])(f: List[FileKey] => Future[Map[FileKey, (String, Array[String])]])(
    implicit
    context:          SpanContext,
    flattener:        HydrationFlattener[From, IN],
    fileKeyExtractor: FileKeyExtractor[IN],
    mapper:           HydrationMapper[FileKey, (String, Array[String]), From, To]): Source[To, Any] = {

    val codeScanInitial = (Map.empty[FileKey, (String, Array[String])], List.empty[To])

    context.withSpanS("query.hydration.code") { cc =>
      in.groupedWithin(CodeJoinBatchSize, CodeJoinLatency).scanAsync(codeScanInitial) {
        case ((codeMap, _), next) => {
          val keys = flattener.flatten(next.toList).flatMap(fileKeyExtractor.extract).distinct

          val (hasKeys, remainingKeys) = keys.partition(k => codeMap.contains(k))

          cc.withSpan(
            "query.hydration.code.pull",
            "size.map" -> codeMap.size.toString(),
            "size.in" -> next.size.toString(),
            "size.keys.in" -> keys.size.toString(),
            "size.remaining" -> remainingKeys.size.toString()) { cc2 =>
              for {
                lookup <- ifNonEmpty(remainingKeys) {
                  cc2.withSpan("query.hydration.code.fetch") { _ =>
                    f(remainingKeys.toList)
                  }
                }
                fullMap = (codeMap ++ lookup)
                nextJoined = cc2.withSpanC(
                  "query.hydration.code.hydrate",
                  "size.code" -> ifNonEmpty(lookup.values) {
                    lookup.values.map(_._1.length).sum.toString()
                  }) { _ =>
                    next.map { n =>
                      mapper.hydrate(n, fullMap)
                    }
                  }
              } yield {
                // println("CODEMAP" + fullMap.size)
                //context.event("query.hydration.code.map", "size" -> lastMap.size.toString())
                (fullMap, nextJoined.toList)
              }
            }
        }
      }.mapConcat(_._2)
    }
  }

  /**
   * Helper
   */
  private def getTextForFiles(keys: List[FileKey]): Future[Map[FileKey, (String, Array[String])]] = {
    for {
      res <- Source(keys).mapAsync(1) { key =>
        val fullPath = s"${models.RepoSHAHelpers.CollectionsDirectory}/${key.key}/${key.path}"
        fileService.readFile(fullPath).map { data =>
          key -> data
        }
      }.runWith(Sink.fold(Map.empty[FileKey, (String, Array[String])]) {
        case (acc, (k, d)) => {
          val dStr = d.utf8String
          acc + (k -> (dStr, dStr.split("\n")))
        }
      })
    } yield {
      res.toMap
    }
  }
}