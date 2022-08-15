package services.gq3

import services._
import models.{ IndexType, ESQuery, Errors, Sinks }
import models.query._
import models.graph._
import models.index.{ NodeType, GraphNode }
import silvousplay.imports._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow, Sink, GraphDSL, Broadcast, MergeSorted, Concat, Merge }
import akka.stream.{ Attributes, FlowShape, OverflowStrategy }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import javax.inject._

@Singleton
class GraphQueryService @Inject() (
  configuration:        play.api.Configuration,
  nodeHydrationService: NodeHydrationService,
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val SearchScroll = 10000

  def runQuery(query: GraphQuery)(implicit targeting: QueryTargeting[TraceUnit]) = {
    runQueryGeneric[TraceUnit, (String, GraphNode), QueryNode](query)
  }

  private def runQueryGeneric[TU, IN, NO](query: GraphQuery)(
    implicit
    targeting:        QueryTargeting[TU],
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    node:             HydrationMapper[TraceKey, JsObject, GraphTrace[TU], GraphTrace[IN]],
    code:             HydrationMapper[FileKey, String, GraphTrace[IN], GraphTrace[NO]]): Future[(QueryResultHeader, Source[GraphTrace[NO], Any])] = {
    for {
      (sizeEstimate, _, traversed) <- executeUnit[TU](query, progressUpdates = false, cursor = None)(targeting)
      rehydrated = nodeHydrationService.rehydrate[TU, IN, NO](traversed)
      traceColumns = query.traverses.filter(_.isColumn).zipWithIndex.map {
        case (_, idx) => QueryColumnDefinition(
          s"trace_${idx}",
          QueryResultType.NodeTrace)
      }
      header = QueryResultHeader(
        isDiff = false,
        sizeEstimate = sizeEstimate,
        columns = traceColumns ++ List(
          QueryColumnDefinition(
            "terminus",
            QueryResultType.NodeTrace)))
    } yield {
      (header, rehydrated)
    }
  }

  def executeUnit[TU](
    query:           GraphQuery,
    progressUpdates: Boolean,
    cursor:          Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU]): Future[(Long, Source[Long, Any], Source[GraphTrace[TU], Any])] = {

    val nodeIndex = targeting.nodeIndexName
    val edgeIndex = targeting.edgeIndexName

    for {
      (rootSize, rootSource) <- rootSearch[TU](query.root, cursor)
      // elasticsearch caps out at 10000 when returning regular query so we do an explicit count
      size <- if (rootSize =?= 10000L && progressUpdates) {
        elasticSearchService.count(
          nodeIndex,
          targeting.rootQuery(query.root)) map (resp => (resp \ "count").as[Long])
      } else {
        Future.successful(rootSize)
      }
      (progressSource, adjustedSource) = if (progressUpdates) {
        // We don't care about dropping progress updates
        val (queue, queueSource) = Source.queue[Long](
          bufferSize = 20,
          OverflowStrategy.dropBuffer).preMaterialize()

        val newSource = rootSource.alsoTo {
          Flow[GraphTrace[TU]].map { v =>
            targeting.getId(v.terminusId)
          }.groupedWithin(2000, 600.milliseconds).scan((0L, "")) {
            case ((count, latestId), ids) => {
              val greater = ids.filter(_ > latestId)
              (count + greater.length, greater.maxByOption(i => i).getOrElse(latestId))
            }
          }.mapAsync(1) {
            // so it's all sequenced
            case (count, _) => {
              queue.offer(count)
            }
          }.to(
            Sink.onComplete({ _ =>
              queue.complete()
            }))
        }

        (queueSource, newSource)
      } else {
        (Source[Long](Nil), rootSource)
      }

      // do edge traverse
      traversed = adjustedSource
      // .via {
      //   executeTrace(query.traverses)
      // }
    } yield {
      (size, progressSource, traversed)
    }
  }

  private def rootSearch[TU](
    root:   GraphRoot,
    cursor: Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU]): Future[(Long, Source[GraphTrace[TU], _])] = {
    for {
      (size, source) <- elasticSearchService.source(
        targeting.nodeIndexName,
        targeting.rootQuery(root),
        sort = targeting.nodeSort,
        additional = cursor.map(_.searchAfter).getOrElse(Json.obj()),
        scrollSize = SearchScroll)
    } yield {
      (size, source.map { i =>
        val traceUnit = targeting.unitFromJs(i)
        GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, traceUnit))
      })
    }
  }

}