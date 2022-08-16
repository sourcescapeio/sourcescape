package services

import silvousplay.TSort
import models.{ Errors, IndexType }
import models.query._
import models.index.GraphNode
import models.index.esprima._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
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

  // honestly, T doesn't matter
  private def runCount[T](
    source: Source[Map[String, T], Any]): Source[Map[String, JsValue], Any] = {
    source.groupedWithin(10000, 500.milliseconds).scan(0) {
      case (acc, items) => acc + items.length
    }.map {
      case cnt => Map(
        "_diffKey" -> Json.toJson("*"),
        "*" -> Json.obj("count" -> cnt))
    }
  }

  private def runDistinct[T, TU, IN](
    source:  Source[Map[String, T], Any],
    columns: List[String],
    named:   List[String])(
    implicit
    targeting:   QueryTargeting[TU],
    flattener:   HydrationFlattener[Map[String, T], TU],
    tracing:     QueryTracing[T, TU],
    hasTraceKey: HasTraceKey[TU],
    groupable:   Groupable[IN],
    node:        HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]]): Source[Map[String, GraphTrace[IN]], Any] = {

    val cSet = columns.toSet
    val nSet = named.toSet
    val filtered = source.map { m =>
      m.view.filterKeys(k => cSet.contains(k)).toMap
    }.map { m =>
      // we only need terminus
      m.map {
        case (k, v) => k -> tracing.newTrace(tracing.getTraceKey(v))
      }
    }

    val nodeJoined = nodeHydrationService.rehydrateNodeMap[T, TU, IN](filtered)

    nodeJoined.statefulMapConcat { () =>
      val existing = collection.mutable.Set.empty[Map[String, String]]

      { item =>
        val keys = item.map {
          case (k, v) if nSet.contains(k) => k -> groupable.name(v.terminusId)
          case (k, v)                     => k -> groupable.id(v.terminusId)
        }

        if (existing.contains(keys)) {
          Nil
        } else {
          existing.add(keys)
          item :: Nil
        }
      }
    }
  }

  private def runGroupedCount[T, TU, IN](
    source:   Source[Map[String, T], Any],
    grouping: RelationalSelect.GroupedCount)(
    hydrateCount: Source[Map[String, T], Any] => Source[Map[String, GraphTrace[IN]], Any])(
    implicit
    groupable: Groupable[IN]): Source[Map[String, JsValue], Any] = {

    // filter for only used keys
    val cSet = (grouping.grip :: grouping.columns).toSet
    val filtered = source.map { m =>
      m.view.filterKeys(k => cSet.contains(k)).toMap
    }

    val nodesHydrated = hydrateCount(filtered)
    nodesHydrated.groupedWithin(10000, 1.second).scan((Map.empty[String, Int], List.empty[Map[String, JsValue]])) {
      case ((acc, _), items) => {
        val grouped = items.groupBy { i =>
          groupable.diffKey(grouping, i)
        }

        // assume display key in line with diffKey
        val withDisplay = grouped.flatMap {
          case (diffKey, vs) => vs.headOption.map { i =>
            (diffKey, groupable.displayKeys(grouping, i)) -> vs
          }
        }

        val calced = withDisplay.map {
          case ((diffKey, displayKeys), vs) => {
            val prevCount = acc.getOrElse(diffKey, 0)
            (diffKey, displayKeys) -> (vs.length + prevCount)
          }
        }

        val nextAcc = calced.map {
          case ((diffKey, _), count) => {
            diffKey -> count
          }
        }

        val diff = calced.toList.map {
          case ((diffKey, displayKeys), count) => {
            displayKeys.map {
              case (k, v) => k -> Json.obj("key" -> v)
            }.toMap ++ Map(
              "_diffKey" -> Json.toJson(diffKey),
              "*" -> Json.obj("count" -> count))
          }
        }

        (acc ++ nextAcc, diff)
      }
    }.mapConcat {
      case (_, diffs) => diffs
    }
  }

  private def hydrate[T, TU, IN, NO](in: Source[Map[String, T], Any])(
    implicit
    targeting:        QueryTargeting[TU],
    tracing:          QueryTracing[T, TU],
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, String, Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
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
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, String, Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    fileKeyExtractor: FileKeyExtractor[IN],
    groupable:        Groupable[IN],
    writes:           Writes[NO]): (List[QueryColumnDefinition], Source[Map[String, JsValue], Any]) = {

    val hydrated = query.select match {
      case RelationalSelect.SelectAll => {
        hydrate(source)
      }
      case RelationalSelect.Select(c) => {
        val cSet = c.toSet
        val filtered = source.map { m =>
          m.view.filterKeys(k => cSet.contains(k)).toMap
        }
        hydrate(filtered)
      }
      case RelationalSelect.CountAll => {
        runCount(source)
      }
      case d @ RelationalSelect.Distinct(c, n) => {
        val distinct = runDistinct[T, TU, IN](source, c, n)

        nodeHydrationService.rehydrateCodeMap(distinct).map { src =>
          src.view.mapValues(_.json).toMap
        }
      }
      case g @ RelationalSelect.GroupedCount(_, _, _) => {
        runGroupedCount[T, TU, IN](source, g) { in =>
          nodeHydrationService.rehydrateNodeMap(in)
        }
      }
    }

    val columns = query.select match {
      case RelationalSelect.GroupedCount(g, t, columns) => {
        (t.identifiers ++ columns).map { k =>
          QueryColumnDefinition(k, QueryResultType.GroupingKey)
        } :+ QueryColumnDefinition("*", QueryResultType.Count)
      }
      case RelationalSelect.CountAll => {
        QueryColumnDefinition("*", QueryResultType.Count) :: Nil
      }
      case RelationalSelect.SelectAll => {
        query.allKeys.map { k =>
          QueryColumnDefinition(k, targeting.resultType)
        }
      }
      case RelationalSelect.Select(columns) => {
        columns.map { k =>
          QueryColumnDefinition(k, targeting.resultType)
        }
      }
      case RelationalSelect.Distinct(columns, named) => {
        columns.map {
          case k if named.contains(k) => QueryColumnDefinition(k, QueryResultType.NameTrace)
          case k                      => QueryColumnDefinition(k, targeting.resultType)
        }
      }
    }

    (columns, hydrated)
  }
}