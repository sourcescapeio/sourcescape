package services

import services._
import silvousplay.TSort
import models.{ Errors, IndexType }
import models.query._
import models.index.esprima._
import models.index.GraphNode
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
import akka.stream.{ Attributes, OverflowStrategy, UniformFanOutShape, FanInShape2, FlowShape }
import akka.stream.scaladsl.{
  Source,
  Flow,
  Sink,
  GraphDSL,
  //
  SourceQueueWithComplete,
  //
  Broadcast,
  Concat,
  Merge,
  Partition
}
import GraphDSL.Implicits._
import models.graph.GenericGraphNode
import akka.stream.scaladsl.Zip
import akka.stream.scaladsl.ZipN
import models.Sinks
import scala.collection.immutable.HashSet

@Singleton
class RelationalQueryService @Inject() (
  configuration:            play.api.Configuration,
  graphQueryService:        GraphQueryService,
  relationalResultsService: RelationalResultsService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  def parseQuery(query: String): Either[fastparse.Parsed.TracedFailure, (Option[RelationalKey], RelationalQuery)] = {
    fastparse.parse(query, RelationalQuery.query(_)) match {
      case fastparse.Parsed.Success(parsed, _) => {
        Right(parsed)
      }
      case f @ fastparse.Parsed.Failure(_, _, _) => {
        Left(f.trace())
      }
    }
  }

  def runQueryInternal[T, TU](
    query:           RelationalQuery,
    progressUpdates: Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU], scroll: QueryScroll) = {

    val cc = context.decoupledSpan("query.relational")
    // do validation
    query.validate()

    // run the root
    val rootQuery = query.root.query
    val initialCursor = scroll.getInitialCursor(query.calculatedOrdering)

    val explain = {
      val emptyLinks = collection.mutable.ListBuffer.empty[(String, String)]
      val emptyKeys = collection.mutable.HashSet.empty[String]
      RelationalQueryExplain(emptyLinks, emptyKeys, None, None)
    }

    val rootKey = query.root.key
    val cc2 = cc.decoupledSpan("query.relational.base", "key" -> rootKey)
    for {
      (size, progressSource, baseF) <- {
        graphQueryService.executeUnit(rootQuery, progressUpdates, initialCursor)(targeting, cc2, tracing)
      }
      base = cc2.terminateFor(baseF)
      joined = if (query.traces.isEmpty) {
        base.map { trace =>
          Map(rootKey -> trace)
        }
      } else {
        createJoinLattice(
          base,
          query)(targeting, cc, tracing, scroll)
      }
      filtered = joined filterNot { i =>
        // filter out violations
        val havingMiss = query.having.exists {
          case (k, HavingFilter.IS_NOT_NULL) => i.get(k).isEmpty
          case (k, HavingFilter.IS_NULL)     => i.get(k).isDefined
        }

        // val havingOrMiss = ifNonEmpty(query.havingOr) {
        //   Option apply query.havingOr.forall {
        //     case (k, HavingFilter.IS_NOT_NULL) => i.get(k).isEmpty
        //     case (k, HavingFilter.IS_NULL) => i.get(k).isEmpty
        //   }
        // }.getOrElse(false)

        val intersectionMiss = {
          query.intersect.exists { inter =>
            inter.map { k =>
              i.get(k).map { v => tracing.getId(tracing.getTerminus(v)) }
            }.distinct.length =/= 1
          }
        }

        //|| havingOrMiss
        havingMiss || intersectionMiss
      }
      scrollOffset = scroll.lastKey.foldLeft(filtered) {
        case (acc, lastKey) => {
          acc.statefulMapConcat { () =>
            var brokenCardinality = false

            {
              case next if brokenCardinality => next :: Nil // pass through
              case next => {
                if (lastKey.lte(
                  query.calculatedOrdering,
                  next.map {
                    case (k, v) => k -> targeting.relationalKeyItem(tracing.getTerminus(v))
                  })) {
                  brokenCardinality = true
                  next :: Nil
                } else {
                  Nil
                }
              }
            }
          }
        }
      }
      offset = query.offset.foldLeft(scrollOffset)((acc, i) => acc.drop(i))
      limited = query.limit.foldLeft(offset)((acc, i) => acc.take(i))
    } yield {
      (size, explain, progressSource, cc.terminateFor(limited))
    }
  }

  def runQuery(query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(implicit targeting: QueryTargeting[TraceUnit], context: SpanContext, scroll: QueryScroll): Future[RelationalQueryResult] = {
    implicit val tracing = QueryTracing.Basic
    runQueryGeneric[GraphTrace[TraceUnit], TraceUnit, (String, GraphNode), QueryNode](query, progressUpdates)
  }

  private def runQueryGeneric[T, TU, IN, NO](query: RelationalQuery, progressUpdates: Boolean)(
    implicit
    targeting:        QueryTargeting[TU],
    context:          SpanContext,
    tracing:          QueryTracing[T, TU],
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    fileKeyExtractor: FileKeyExtractor[IN],
    writes:           Writes[NO],
    //
    scroll: QueryScroll): Future[RelationalQueryResult] = {
    val ordering = query.calculatedOrdering // calculate beforehand
    println(QueryString.stringifyScroll(scroll))
    println(QueryString.stringify(query))

    for {
      (size, explain, progressSource, source) <- runQueryInternal(query, progressUpdates)
      // hydration
      (columns, hydrated) <- relationalResultsService.hydrateResults[T, TU, IN, NO](source, query)
    } yield {
      RelationalQueryResult(
        size,
        progressSource,
        isDiff = false,
        columns,
        hydrated,
        explain)
    }
  }

  def runWithoutHydration(query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(implicit targeting: QueryTargeting[TraceUnit], context: SpanContext, scroll: QueryScroll): Future[Source[Map[String, GraphTrace[TraceUnit]], _]] = {
    implicit val tracing = QueryTracing.Basic
    runQueryGenericWithoutHydration[GraphTrace[TraceUnit], TraceUnit, (String, GraphNode), QueryNode](query, explain, progressUpdates)
  }

  private def runQueryGenericWithoutHydration[T, TU, IN, NO](query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(
    implicit
    targeting:        QueryTargeting[TU],
    context:          SpanContext,
    tracing:          QueryTracing[T, TU],
    hasTraceKey:      HasTraceKey[TU],
    flattener:        HydrationFlattener[Map[String, T], TU],
    node:             HydrationMapper[TraceKey, JsObject, Map[String, T], Map[String, GraphTrace[IN]]],
    code:             HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    fileKeyExtractor: FileKeyExtractor[IN],
    writes:           Writes[NO],
    //
    scroll: QueryScroll) = {
    val ordering = query.calculatedOrdering // calculate beforehand

    for {
      (_, _, _, source) <- runQueryInternal(query, progressUpdates)
      // hydration
    } yield {
      source
    }
  }

  /**
   * Generate the join stream lattice
   */
  type Joined[T] = Map[String, T]

  private def createJoinLattice[T, TU](
    root:  Source[T, Any],
    query: RelationalQuery)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU], scroll: QueryScroll): Source[Joined[T], Any] = {
    val rootQuery = query.root.query

    /**
     * Prelim info
     */
    val rootKey = query.root.key

    // Find terminal join nodes
    // Use reverse topologicalSort ordering
    // "for every directed edge uv from vertex u to vertex v, u comes before v in the ordering"
    // This guarantees right ordering of final joins
    val joinLattice = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      /**
       * Calculate all the broadcasts.
       * - Every node has a broadcast flow
       * - Its size depends on the number of edges to other nodes
       * - But also need to add 1 for pushing to a joiner
       */
      val queryDependencyCounts: Map[String, Int] = {
        calculateQueryDependencyCounts(query)
      }

      val broadcastMap = queryDependencyCounts.map {
        case (k, size) => {
          // add one to route to joiner
          k -> builder.add(Broadcast[Joined[T]](size))
        }
      }

      /**
       * Link the root with its join broadcast
       */
      val rootCast = broadcastMap.get(rootKey).getOrElse {
        throw new Exception("could not find root cast")
      }

      /**
       * Link the node broadcasts together
       * - Also link with the joiner
       */
      val allLeft = query.traces.groupBy(_.query.fromName).flatMap {
        case (k, vs) if vs.forall(_.query.from.leftJoin) => Some(k)
        case _ => None
      }.toSet

      query.traces.foreach { trace =>
        applyTraceToGraph(
          trace,
          broadcastMap,
          allLeft.contains(trace.query.fromName))
      }

      /**
       * Final Join system
       */
      val joinTree = getFinalJoinTree(query, broadcastMap)

      println("JOINTREE", joinTree)

      val finalMerge = joinTree.merge(query)

      akka.stream.FlowShape(rootCast.in, finalMerge.out(0))
    }).log("fulljoin").addAttributes(Attributes.logLevels(
      onElement = Attributes.LogLevels.Info,
      onFinish = Attributes.LogLevels.Warning,
      onFailure = Attributes.LogLevels.Error))

    root.map { i =>
      Map(rootKey -> i)
    }.via(joinLattice)
  }

  /**
   * Helpers
   */
  private def calculateQueryDependencyCounts(query: RelationalQuery): Map[String, Int] = {
    val byFrom = query.traces.groupBy(_.query.fromName)
    val allFroms: Map[String, Int] = byFrom.map {
      case (k, vs) => {
        val allLeft = vs.forall(_.query.from.leftJoin)
        k -> vs.length
      }
    }

    // this is important
    val allKeys = query.allKeys
    allKeys.map { k =>
      k -> allFroms.getOrElse(k, 1)
    }.toMap
  }

  private def applyTraceToGraph[T, TU](
    trace:        KeyedQuery[TraceQuery],
    broadcastMap: Map[String, UniformFanOutShape[Joined[T], Joined[T]]],
    isLeft:       Boolean)(
    implicit
    builder:   GraphDSL.Builder[Any],
    targeting: QueryTargeting[TU],
    context:   SpanContext,
    tracing:   QueryTracing[T, TU]) = {
    val fromKey = trace.query.fromName
    val toKey = trace.key

    val fromCast = broadcastMap.get(fromKey).getOrElse {
      throw new Exception("could not find input:" + fromKey)
    }
    val toCast = broadcastMap.get(toKey).getOrElse {
      throw new Exception("could not find output:" + toKey)
    }

    /**
     * Build up query flow [from -> to]
     */
    // Mapped
    val mappedTracing = MapTracing(tracing, fromKey, toKey)

    val flow = context.withSpanF("query.relational.trace", "key" -> toKey) { cc =>
      graphQueryService.executeTrace[Map[String, T], TU](
        trace.query.traverses)(targeting, cc, mappedTracing)
    }

    if (!isLeft) {
      // may need to filter out not found
      fromCast ~> Flow[Map[String, T]].filter(_.contains(fromKey)).map { t =>
        context.event("trace-in", "key" -> toKey, "size" -> "1", "left" -> "false")
        mappedTracing.pushExternalKey(t)
      } ~> flow.log(s"flow[$fromKey,$toKey]").map { t =>
        context.event("trace-out", "key" -> toKey)
        t
      } ~> toCast
    } else {
      // If self left-join, we use a hash join with self
      val getKey = { item: Map[String, T] =>
        val fromItem = item.getOrElse(fromKey, throw new Exception("invalid merged item"))
        tracing.joinKey(fromItem).mkString("|")
      }

      fromCast ~> Flow[Map[String, T]].filter(_.contains(fromKey)).map { t =>
        mappedTracing.pushExternalKey(t)
      }.groupedWithin(1000, 100.milliseconds).mapAsync(1) { batch =>
        context.event("trace-in", "key" -> toKey, "size" -> batch.size.toString(), "left" -> "true")
        for {
          res <- Source(batch).via(flow).statefulMapConcat { () =>
            //
            val batchValues = batch.groupBy(getKey).toSeq
            val batchMap = scala.collection.mutable.SortedMap(batchValues: _*)

            {
              case ele => {
                val currKey = getKey(ele)

                // get all in state < currKey O(n to currKey)
                val ltValues = batchMap.takeWhile {
                  case (k, v) => k < currKey
                }

                // UGH.
                batchMap.filterInPlace {
                  case (k, v) => k > currKey
                }
                // make sure to remove currKey when we clean up this algo
                // batchMap.remove(currKey)

                val sortedValues = ltValues.toList.flatMap {
                  case (k, v) => v.map(_ - toKey) // to reverse pushExternalKey. probably better way of doing this
                }
                // don't think we need this as already sorted
                // .sortBy { i =>
                //   tracing.sortKey(i.getOrElse(fromKey, throw new Exception("fail"))).mkString("|")
                // }

                List(sortedValues.toSeq: _*) ++ List(ele)
              }
            }
          }.runWith(Sinks.ListAccum)
          // this could easily blow up, so we want to run it thru a window
          // missing batch
        } yield {
          res
        }
      }.mapConcat(i => i) ~> toCast
    }
  }

  /**
   * Final Merge
   * Joins all terminal broadcasts as well as left joins together
   */
  sealed trait JoinUnit[T] {
    def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], context: SpanContext): UniformFanOutShape[Joined[T], Joined[T]]

    val key: String

    val reportKey: String

    val isLeft: Boolean
  }

  private case class JoinTerminus[T](key: String, cast: UniformFanOutShape[Joined[T], Joined[T]], leftJoin: Boolean) extends JoinUnit[T] {
    def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], context: SpanContext) = cast

    val reportKey = key

    val isLeft = leftJoin
  }

  // joinKey is the root (!)
  // A fork to B, C, we join using A as joinKey (!!!)
  private case class JoinTree[T, TU](joinKey: String, a: JoinUnit[T], b: JoinUnit[T], leftJoin: Boolean)(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) extends JoinUnit[T] {

    val key = joinKey

    val reportKey = s"[${a.reportKey}<>${b.reportKey}]"

    val isLeft = leftJoin

    def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], context: SpanContext) = {
      val getKey = { item: Joined[T] =>
        val fromItem = item.getOrElse(joinKey, throw new Exception("invalid merged item"))
        tracing.joinKey(fromItem)
      }

      val aKey = { item: Joined[T] =>
        val fromItem = item.getOrElse(a.key, throw new Exception("invalid merged item"))
        tracing.joinKey(fromItem)
      }

      val bKey = { item: Joined[T] =>
        val fromItem = item.getOrElse(b.key, throw new Exception("invalid merged item"))
        tracing.joinKey(fromItem)
      }

      val isLeft = a.isLeft || b.isLeft

      val explainKey = isLeft match {
        case false => s"${a.key}<>${b.key}[${joinKey}]"
        case true  => s"${a.key}<>${b.key}[${joinKey}]:left"
      }

      val out = builder.add(Broadcast[Joined[T]](1))

      val cc = context.decoupledSpan("query.relational.join", "key" -> reportKey)

      val nextJoin = buildMergeJoin(
        a.merge(query),
        b.merge(query),
        leftOuter = b.isLeft, // isLeft || query.shouldOuterJoin(b.key), // opposite, yeah it's weird
        rightOuter = a.isLeft // isLeft || query.shouldOuterJoin(a.key)
      )(
          getKey,
          aKey,
          bKey)(builder, cc, implicitly[Ordering[Vector[String]]], implicitly[Writes[Vector[String]]])

      nextJoin.out ~> cc.terminateFor {
        Flow[(Vector[String], (Option[Joined[T]], Option[Joined[T]]))].map {
          case (k, (maybeA, maybeB)) => {
            maybeA.getOrElse(Map()) ++ maybeB.getOrElse(Map())
          }
        }
      } ~> out

      out
    }
  }

  /**
   * Algo:
   * 1. Start with leaves
   * 2. Get all leaves from common root. Lookup left joins to include with this
   */
  private def getLeaves[TU](
    traceQueries: List[KeyedQuery[TraceQuery]],
    joincastMap:  Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]): List[(String, JoinTerminus[TU])] = {
    val froms = traceQueries.map(_.query.fromName).toSet
    val traceMap = traceQueries.map { t =>
      t.key -> t.query
    }

    traceMap.filter {
      case (k, _) => !froms.contains(k)
    }.map {
      case (k, q) => {
        q.fromName -> JoinTerminus(
          k,
          joincastMap.getOrElse(k, throw new Exception(s"join ${k} not found")),
          q.from.leftJoin)
      }
    }
  }

  private def getLeftJoinLeaves[TU](
    leftJoinMap: Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]): List[(String, JoinTerminus[TU])] = {

    leftJoinMap.toList.map {
      case (f, item) => {
        f -> JoinTerminus(
          s"${f}'",
          item,
          true)
      }
    }
  }

  /**
   * Join tree ordering:
   * This accepts an ordering list that will determine the order of the outer joins
   * Outer joins will happen left to right
   *
   */
  private def getFinalJoinTree[T, TU](
    query:       RelationalQuery,
    joincastMap: Map[String, UniformFanOutShape[Joined[T], Joined[T]]])(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) = {
    val traceQueries = query.traces

    val toLookup = traceQueries.map { trace =>
      trace.query.fromName -> trace.key
    }.groupBy(_._1).map {
      case (k, vs) => k -> vs.map(_._2)
    }

    val fromLookup = query.fromLookup
    val ordering = query.calculatedOrdering

    val leaves = getLeaves(traceQueries, joincastMap)

    val collapsed = leaves.map(_._2.key).toSet

    val traceMap = traceQueries.map { t =>
      t.key -> t.query
    }.toMap

    def buildLayer(rootKey: String, units: List[JoinUnit[T]]): JoinUnit[T] = {
      units match {
        case Nil         => throw new Exception("0 unit layer")
        case head :: Nil => head
        case head :: second :: other => {
          val leftJoin = traceMap.get(rootKey).map(_.from.leftJoin).getOrElse(false) // if not found, it's the root

          buildLayer(
            rootKey,
            JoinTree[T, TU](rootKey, head, second, leftJoin) :: other)
        }
      }
    }

    def collapseTree(current: List[(String, JoinUnit[T])], collapsed: Set[String]): JoinUnit[T] = {
      val baseLayer = current.groupBy(_._1).map {
        case (k, vs) => (k, vs.map(_._2).sortBy(i => ordering.indexOf(i.key))) // need to make sure follows TSort ordering
      }

      // find a current root where all leaves are collapsed
      val chooseRoot = baseLayer.find {
        case (from, _) => {
          toLookup.getOrElse(from, Nil) match {
            case Nil   => false
            case other => other.forall(collapsed.contains)
          }
        }
      }

      chooseRoot match {
        case Some((r, toMerge)) if fromLookup.contains(r) => {
          val currentRemoved = (baseLayer - r).toList.flatMap {
            case (k, vs) => vs.map(k -> _)
          }.toList
          val nextFrom = fromLookup.get(r).get
          val nextLayer = buildLayer(r, toMerge)

          collapseTree(
            (nextFrom, nextLayer) :: currentRemoved,
            collapsed + r)
        }
        case Some((r, toMerge)) => {
          // terminal
          buildLayer(r, toMerge)
        }
        case None => throw new Exception("unable to choose root and recurse")
      }
    }

    collapseTree(leaves, collapsed)
  }

  /**
   * MERGE JOINS
   */
  implicit val ordering: Ordering[Vector[String]] = Ordering.by { in: Vector[String] =>
    in.mkString("|")
  }

  private def buildMergeJoin[K, V](
    left:       UniformFanOutShape[V, V],
    right:      UniformFanOutShape[V, V],
    leftOuter:  Boolean,
    rightOuter: Boolean)(
    rootKey: V => K,
    //
    v1SecondaryKey: V => K,
    v2SecondaryKey: V => K)(implicit builder: GraphDSL.Builder[Any], context: SpanContext, ordering: Ordering[K], writes: Writes[K]) = {
    val mergeCC = context.decoupledSpan("query.relational.join.merge")
    val doExplain = true
    val joiner = builder.add(new MergeJoin[K, V, V](
      mergeCC,
      doExplain,
      leftOuter = leftOuter,
      rightOuter = rightOuter
    // v1SecondaryKey,
    // v2SecondaryKey
    ))

    // calculate keys
    left ~> Flow[V].map {
      case joined => {
        val key = rootKey(joined)
        (key, joined)
      }
    } ~> joiner.in0

    right ~> mergeCC.terminateFor(Flow[V].map {
      case trace => {
        val key = rootKey(trace)
        (key, trace)
      }
    }) ~> joiner.in1

    // wrong termination, but let's run with it
    joiner
  }
}
