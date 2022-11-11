package services

import silvousplay.TSort
import models.{ Errors, IndexType }
import models.query._
import models.index.GraphNode
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
import akka.stream.scaladsl.Source

private case class GroupingState(state: Map[String, Int], diff: Map[String, Int]) {

}

private object GroupingState {
  def empty = GroupingState(Map.empty[String, Int], Map.empty[String, Int])
}

@Singleton
class RelationalResultsService @Inject() (
  nodeHydrationService: NodeHydrationService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  private def hydrate[T, TU, IN, NO](in: Source[Map[String, T], Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[T, TU],
    context:          SpanContext,
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    fileKeyExtractor: FileKeyExtractor[IN],
    writes:           Writes[NO]): Source[Map[String, JsValue], Any] = {
    nodeHydrationService.rehydrateMap[T, TU, IN, NO](in).map { src =>
      src.view.mapValues(_.json).toMap
    }
  }

  def hydrateResults[T, TU, IN, NO](
    source: Source[Map[String, T], Any],
    query:  RelationalQuery)(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[T, TU],
    context:          SpanContext,
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    fileKeyExtractor: FileKeyExtractor[IN],
    writes:           Writes[NO]): (List[QueryColumnDefinition], Source[Map[String, JsValue], Any]) = {

    // filter down to just the columns we need to hydrate
    val hydrated = {
      val cSet = query.select.flatMap(_.columns).toSet

      val filtered = source.map { m =>
        m.view.filterKeys(k => cSet.contains(k)).toMap
      }
      hydrate(filtered)
      // then need to construct
    }

    // calculate SELECTs
    val calculated = {
      hydrated.map { mm =>
        query.select.map { s =>
          s.key -> s.applySelect(mm)
        }.toMap
      }
    }

    val columns = query.select.map { s =>
      QueryColumnDefinition(s.key, s.resultType) // need name
    }

    (columns, calculated)
  }
}
