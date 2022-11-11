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
import akka.stream.scaladsl._
import scala.concurrent.Promise
import akka.stream.Graph
import akka.stream.SinkShape
import akka.stream.UniformFanOutShape
import akka.NotUsed
import models.query.RelationalSelect.Column
import models.query.RelationalSelect.Member
import models.query.RelationalSelect.Operation
import models.Sinks

private trait HydrationTable {

  def add(next: Map[(String, String), JsValue]): Future[HydrationTable]

  def getOrError(k: (String, String)): Future[JsValue]

}

@Singleton
class RelationalResultsService @Inject() (
  nodeHydrationService: NodeHydrationService,
  redisService:         RedisService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

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
    writes:           Writes[NO]): Future[(List[QueryColumnDefinition], Source[Map[String, JsValue], Any])] = {

    val columns = query.select.map { s =>
      QueryColumnDefinition(s.key, s.resultType) // need name
    }

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
    for {
      calculated <- {
        // check if there's a grouped column
        val aggregate = query.select.flatMap {
          case r @ RelationalSelect.Operation(_, opType, _) if opType.aggregate => Some(r.toGrouped)
          case _ => None
        }

        val notAggregate = query.select.flatMap {
          case RelationalSelect.Operation(_, opType, _) if opType.aggregate => None
          case r => Some(r)
        }

        aggregate match {
          case Nil => Future.successful {
            hydrated.map { mm =>
              query.select.map { s =>
                s.key -> s.applySelect(mm)
              }.toMap
            }
          }
          case aggregators if notAggregate.isEmpty => {
            runGroupedAll(hydrated, aggregators)
          }
          case aggregators => {
            runGrouped(hydrated, notAggregate, aggregators)
          }
        }
      }
    } yield {
      (columns, calculated)
    }
  }

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

  // single aggregator or only one group

  private val GroupSize = 1000

  // multiple sinks but single value per sink
  private def runGroupedAll(
    in:              Source[Map[String, JsValue], Any],
    aggregateFields: List[RelationalSelect.GroupedOperation]): Future[Source[Map[String, JsValue], Any]] = {

    val aggregators = aggregateFields.map { op =>
      Flow[Map[String, JsValue]].groupedWithin(GroupSize, 100.milliseconds).toMat {
        Sink.fold[JsValue, Seq[Map[String, JsValue]]](JsNull) {
          case (acc, next) => {
            op.applyGrouped(acc, next)
          }
        }
      }(Keep.right).mapMaterializedValue { f =>
        f.map(op.key -> _)
      }
    }

    val combined = combineSeq(aggregators)(Broadcast[Map[String, JsValue]](_))

    for {
      v <- Future.sequence(in.runWith(combined))
    } yield {
      Source(List(v.toMap))
    }
  }

  // Sink.combine from akka 2.7.0
  private def combineSeq[T, U, M](sinks: Seq[Graph[SinkShape[U], M]])(
    fanOutStrategy: Int => Graph[UniformFanOutShape[T, U], NotUsed]): Sink[T, Seq[M]] = {
    sinks match {
      case Seq()     => Sink.cancelled.mapMaterializedValue(_ => Nil)
      case Seq(sink) => sink.asInstanceOf[Sink[T, M]].mapMaterializedValue(_ :: Nil)
      case _ =>
        Sink.fromGraph(GraphDSL.create(sinks) { implicit b => shapes =>
          import GraphDSL.Implicits._
          val c = b.add(fanOutStrategy(sinks.size))
          for ((shape, idx) <- shapes.zipWithIndex)
            c.out(idx) ~> shape
          SinkShape(c.in)
        })
    }
  }

  val MaxHydrationTableSize = 10000
  private case class MemoryHydrationTable(v: Map[(String, String), JsValue]) extends HydrationTable {

    def add(next: Map[(String, String), JsValue]): Future[HydrationTable] = {
      val joined = v ++ next
      if (joined.size > MaxHydrationTableSize) {
        val newTable = RedisHydrationTable(Hashing.uuid())
        newTable.add(joined).map(_ => newTable)
      } else {
        Future.successful {
          this.copy(v = joined)
        }
      }
    }

    def getOrError(k: (String, String)): Future[JsValue] = {
      Future.successful {
        v.getOrElse(k, throw new Exception(s"unable to hydrate coerced id ${k}"))
      }
    }

  }

  private case class RedisHydrationTable(hashKey: String) extends HydrationTable {

    private def key(k: (String, String)) = {
      s"${k._1}||${k._2}"
    }

    def add(next: Map[(String, String), JsValue]): Future[HydrationTable] = {
      val shifted = next.map {
        case (k, v) => key(k) -> Json.stringify(v)
      }
      redisService.redisClient.hmset(hashKey, shifted) map (_ => this)
    }

    def getOrError(k: (String, String)): Future[JsValue] = {
      for {
        maybeV <- redisService.redisClient.hget[String](hashKey, key(k))
      } yield {
        maybeV.map(Json.parse).getOrElse {
          throw new Exception(s"unable to hydrate coerced id ${k}")
        }
      }
    }
  }

  private def runGrouped(
    in:         Source[Map[String, JsValue], Any],
    groupBy:    List[RelationalSelect],
    aggregates: List[RelationalSelect.GroupedOperation]): Future[Source[Map[String, JsValue], Any]] = {

    // Coerce string
    val groupers = groupBy map {
      case c @ Column(id) => Member(Some(id), c, MemberType.Id) // We're coercing here
      case m @ Member(name, column, memberType) => m
      case o @ Operation(name, opType, operands) if !opType.aggregate => o
      case i => throw new Exception(s"invalid group ${QueryString.stringifySelect(i)}")
    }

    val hydrationLookupSet = groupBy.flatMap {
      case c @ Column(id) => Some(id)
      case _              => None
    }.toSet

    type GroupedIn = Seq[Map[String, JsValue]]
    // dump coerced columns to be rehydrated later
    val hydrationFlow = Sink.foldAsync[HydrationTable, GroupedIn](MemoryHydrationTable(Map())) {
      case (acc, next) => {
        val nextMap: Map[(String, String), JsValue] = next.flatMap { n =>
          n.flatMap {
            case (k, v) if hydrationLookupSet.contains(k) => {
              Option {
                (k, MemberType.Id.extract[String](v)) -> v
              }
            }
            case _ => None
          }
        }.toMap

        acc.add(nextMap)
      }
    }

    // All grouping is in memory

    type GroupMapValue = (List[(String, JsValue)], Map[String, JsValue])
    val groupingFlow = Sink.fold[Map[String, GroupMapValue], GroupedIn](Map()) {
      case (acc, next) => {
        // in the map we can store some extra info for replacements
        val grouped: Map[List[(String, JsValue)], GroupedIn] = next.groupBy { vv =>
          groupers.map { g =>
            (g.key, g.applySelect(vv))
          }
        }

        val nextMap = grouped.map {
          case (k, v) => {
            val stringKey = k.map(kk => Json.stringify(kk._2)).mkString("||")
            val prevMap = acc.get(stringKey).map(_._2).getOrElse(Map())

            val vv = aggregates.map { i =>
              val prevVal = prevMap.getOrElse(i.key, JsNull)
              i.key -> i.applyGrouped(prevVal, v)
            }.toMap

            stringKey -> (k, vv)
          }
        }

        acc ++ nextMap
      }
    }

    val combinedFlow = Flow[Map[String, JsValue]].groupedWithin(GroupSize, 100.milliseconds).toMat {
      combineTup(hydrationFlow, groupingFlow)
    }(Keep.right)

    val (hydrationTableF, groupedDataF) = in.runWith(combinedFlow)

    for {
      hydrationTable <- hydrationTableF
      groupedData <- groupedDataF
    } yield {
      Source(groupedData).mapAsync(4) {
        case (_, (keys, values)) => {
          for {
            keysMapped <- Source(keys).mapAsync(20) {
              case (k, v) if hydrationLookupSet.contains(k) => {
                val id = v.as[String]
                hydrationTable.getOrError((k, id)).map { vv =>
                  (k, vv)
                }
              }
              case o => Future.successful(o)
            }.runWith(Sinks.ListAccum)
          } yield {
            values ++ keysMapped.toMap
          }
        }
      }
    }
  }

  private def combineTup[T, M1, M2](sink1: Graph[SinkShape[T], M1], sink2: Graph[SinkShape[T], M2]): Sink[T, (M1, M2)] = {
    Sink.fromGraph(GraphDSL.createGraph(sink1, sink2)((_, _)) { implicit b => (shape1, shape2) =>
      import GraphDSL.Implicits._
      val bCast = Broadcast[T](2)
      val c = b.add(bCast)
      c.out(0) ~> shape1.in
      c.out(1) ~> shape2.in

      SinkShape(c.in)
    })
  }

  //   // approach >> we could stream into multiple groups assuming that number of groups is small
  //   // groups

  //   // create a grouper per groupBy

  //   // if there's more than one we need to join and align
  //   val combined = Sink.combine(aggregators)(Broadcast[Map[String, JsValue]](_))

  //   for {
  //     v <- Future.sequence(in.runWith(combined))
  //   } yield {
  //     ()
  //   }

  //   // join groupers by

  //   // stream source into them

  //   Future.successful {

  //     in
  //     // in.map { mm =>
  //     //   query.select.map { s =>
  //     //     s.key -> s.applySelect(mm)
  //     //   }.toMap
  //     // }
  //   }
  // }
}
