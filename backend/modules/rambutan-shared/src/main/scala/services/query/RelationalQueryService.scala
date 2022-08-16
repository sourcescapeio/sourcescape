package services

import silvousplay.TSort
import models.{ Errors, IndexType }
import models.query._
import models.index.esprima._
import models.index.GraphNode
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
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

@Singleton
class RelationalQueryService @Inject() (
  configuration:        play.api.Configuration,
  graphQueryService:    GraphQueryService,
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

  def runQueryInternal[TU](
    query: RelationalQuery, 
    shouldExplain: Boolean, 
    progressUpdates: Boolean
  )(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]], scroll: QueryScroll) = {
    // do validation
    query.validate

    // run the root
    val rootQuery = query.root.query
    val initialCursor = scroll.getInitialCursor(query.calculatedOrdering)

    // link the traces
    for {
      (size, progressSource, base) <- graphQueryService.executeUnit(rootQuery, progressUpdates, initialCursor)
      rootKey = query.root.key
      explain = {
        val emptyLinks = collection.mutable.ListBuffer.empty[(String, String)]
        val emptyKeys = collection.mutable.HashSet.empty[String]
        withFlag(shouldExplain) {
          val queueSource = Source.queue[ExplainMessage](1000, OverflowStrategy.backpressure) // should we .fail?
          Option(queueSource.preMaterialize())
        } match {
          case Some((queue, source)) => {
            RelationalQueryExplain(emptyLinks, emptyKeys, Some(source), Some(queue))
          }
          case _ => {
            RelationalQueryExplain(emptyLinks, emptyKeys, None, None)
          }
        }
      }
      joined = if (query.traces.isEmpty) {
        base.map { trace =>
          Map(rootKey -> trace)
        }
      } else {
        createJoinLattice(
          base,
          query)(targeting, tracing, scroll, explain)
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
              i.get(k).map { v => tracing.getId(v)}
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
                if (
                  lastKey.lte(
                    query.calculatedOrdering, 
                    next.map {
                      case (k, v) => k -> targeting.relationalKeyItem(v.terminusId)
                    }
                  )
                ) {
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
      (size, explain, progressSource, limited)
    }
  }

  def runQuery(query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(implicit targeting: QueryTargeting[TraceUnit], scroll: QueryScroll): Future[RelationalQueryResult] = {
    implicit val tracing = QueryTracing.Basic    
    runQueryGeneric[TraceUnit, (String, GraphNode), QueryNode](query, explain, progressUpdates)
  }

  def runQueryGenericGraph(query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(implicit targeting: QueryTargeting[GenericGraphUnit], scroll: QueryScroll): Future[RelationalQueryResult] = {
    implicit val tracing = QueryTracing.GenericGraph    
    runQueryGeneric[GenericGraphUnit, GenericGraphNode, GenericGraphNode](query, explain, progressUpdates)
  }

  private def runQueryGeneric[TU, IN, NO](query: RelationalQuery, explain: Boolean, progressUpdates: Boolean)(
    implicit targeting: QueryTargeting[TU],
    tracing: QueryTracing[GraphTrace[TU]],
    hasTraceKey: HasTraceKey[TU],
    node:          HydrationMapper[TraceKey, JsObject, Map[String, GraphTrace[TU]], Map[String, GraphTrace[IN]]],
    code:          HydrationMapper[FileKey, String, Map[String, GraphTrace[IN]], Map[String, GraphTrace[NO]]],
    groupable: Groupable[IN],
    fileKeyExtractor: FileKeyExtractor[IN],
    writes: Writes[NO],
    //
    scroll: QueryScroll
  ): Future[RelationalQueryResult] = {
    val ordering = query.calculatedOrdering // calculate beforehand
    println(QueryString.stringifyScroll(scroll))
    println(QueryString.stringify(query))
    for {
      (size, explain, progressSource, source) <- runQueryInternal(query, explain, progressUpdates)
      // hydration
      (columns, hydrated) = relationalResultsService.hydrateResults[TU, IN, NO](source, query)
    } yield {
      val columnKeys = columns.map(_.name).toSet
      RelationalQueryResult(
        size, 
        progressSource, 
        query.isDiff,
        columns,
        hydrated, 
        explain
      )
    }
  }
  
  /**
   * Generate the join stream lattice
   */
  type Joined[TU] = Map[String, GraphTrace[TU]]

  private def createJoinLattice[TU](
    root:    Source[GraphTrace[TU], Any],
    query:   RelationalQuery)(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]], scroll: QueryScroll, explain: RelationalQueryExplain): Source[Joined[TU], Any] = {
    val rootQuery = query.root.query

    /**
     * Prelim info
     */
    val queryDependencyCounts: Map[String, Int] = {
      calculateQueryDependencyCounts(query)
    }

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
      val broadcastMap = queryDependencyCounts.map {
        case (k, size) => {
          // add one to route to joiner
          k -> builder.add(Broadcast[GraphTrace[TU]](size + 1))
        }
      }

      // broadcasts for each of the joins
      // Need to broadcast join to new flow as well as other joins
      val joincastMap = queryDependencyCounts.map {
        case (k, size) => {
          // if none, then route to exit
          k -> builder.add(Broadcast[Joined[TU]](math.max(size, 1)))
        }
      }

      /**
       * Link the root with its join broadcast
       */
      val rootKey = query.root.key
      val rootCast = broadcastMap.get(rootKey).getOrElse {
        throw new Exception("could not find root cast")
      }
      val rootJoin = joincastMap.get(rootKey).getOrElse {
        throw new Exception("could not find root join")
      }
      rootCast ~> Flow[GraphTrace[TU]].map { item =>
        Map(rootKey -> item)
      } ~> rootJoin

      /**
       * Link the node broadcasts together
       * - Also link with the joiner
       */
      // kind of janky to return left join, but better than passing in mutable list
      val leftJoins = query.traces.flatMap { trace =>
        applyTraceToGraph(
          trace,
          broadcastMap,
          joincastMap)
      }.toMap

      /**
       * Final Join system
       */
      // find all terminal nodes
      // fold across, joining at shared common node in tree
      val finalMerge = calculateFinalMerge(
        query,
        joincastMap,
        leftJoins)

      akka.stream.FlowShape(rootCast.in, finalMerge.out(0))
    }).log("fulljoin").addAttributes(Attributes.logLevels(
      onElement = Attributes.LogLevels.Info,
      onFinish = Attributes.LogLevels.Warning,
      onFailure = Attributes.LogLevels.Error))

    root.via(joinLattice)
  }

  /**
   * Helpers
   */
  // TODO: better name
  private def calculateQueryDependencyCounts(query: RelationalQuery): Map[String, Int] = {
    val allKeys = query.allKeys
    val allFroms: Map[String, Int] = {
      query.traces.map(_.query.fromName -> 1).groupBy(_._1).view.mapValues(_.length).toMap
    }

    // this is important
    allKeys.map { k =>
      k -> allFroms.getOrElse(k, 0)
    }.toMap
  }

  /**
                                                 [to other cast]
  +----------+    queryFlow     +---------+      (like fromCast)
  | fromCast +----------------->+ outCast +-------------->
  +---+------+                  +----+----+
      |                              |
                                     +---------+
      |                                        |                              [to other join]
              +----------+   joinFlow      +---v----+  passNext  +--------+   (like fromJoin)
      +- - - -> fromJoin +-----------------> joiner +-----+----->+joinCast+--------->
    linked    +----------+                 +--------+     |      +--------+
    thru                                                  |
    previous                                              |    +----------+  [returned]
    trace                                                 +--->+ leftCast |--------->
    apply                                                      +----------+
                                                       maybeLeft
  */
  private def applyTraceToGraph[TU](
    trace:        KeyedQuery[TraceQuery],
    broadcastMap: Map[String, UniformFanOutShape[GraphTrace[TU], GraphTrace[TU]]],
    joincastMap:  Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]])(implicit builder: GraphDSL.Builder[Any], targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]], explain: RelationalQueryExplain): Option[(String, UniformFanOutShape[Joined[TU], Joined[TU]])] = {
    val fromKey = trace.query.fromName
    val toKey = trace.key

    // previous node
    val fromCast = broadcastMap.get(fromKey).getOrElse {
      throw new Exception("could not find input:" + fromKey)
    }
    val fromJoin = joincastMap.get(fromKey).getOrElse {
      throw new Exception("could not find input:join:" + fromKey)
    }

    // current node
    val outCast = broadcastMap.get(toKey).getOrElse {
      throw new Exception("could not find output:" + toKey)
    }
    val joinCast = joincastMap.get(toKey).getOrElse {
      throw new Exception("could not find output:join:" + toKey)
    }

    /**
     * Build up query flow [from -> to]
     */
    /**
     * Build up MergeJoin [(from.join, to) -> joiner]
     */
    val isLeftJoin = trace.query.from.leftJoin
    
    if (trace.query.traverses.isEmpty && !isLeftJoin) {
      // Don't build a whole join thing if we're just passing through
      passthroughJoin(
        fromCast,
        outCast,
        fromJoin,
        joinCast,
        fromKey,
        toKey
      )

      None
    } else {
      val flow = graphQueryService.executeTrace(
        trace.query.traverses)

      val pushExplain = explain.pusher(toKey)

      fromCast ~> Flow[GraphTrace[TU]].map { t =>
        pushExplain(("buffer", Json.obj("action" -> "trace-in", "key" -> toKey)))
        tracing.pushExternalKey(t)
      } ~> flow.log(s"flow[$fromKey,$toKey]").map { t => 
        pushExplain(("buffer", Json.obj("action" -> "trace-out", "key" -> toKey)))
        t
      } ~> outCast

      explain.link(fromKey, toKey)

      val joiner = buildMergeJoin(
        fromJoin,
        outCast,
        // explain.pusher(toKey),
        pushExplain,
        leftOuter = isLeftJoin,
        rightOuter = false)(
        { joined =>
          val fromItem = joined.get(fromKey).getOrElse {
            throw Errors.streamError("invalid merged item")
          }
          tracing.joinKey(fromItem)
        },
        { v => tracing.sortKey(v) })

      /**
       * Link join output to join broadcast. Left joins have separate thing
       * [joiner -> to.join]
       */
      if (isLeftJoin) {
        Option(linkJoinToNextWithLeftJoin(joiner, toKey, joinCast, explain.pusher(toKey)))
      } else {
        linkJoinToNext(joiner, toKey, joinCast, explain.pusher(toKey))
        None
      }
    }
  }

  private def passthroughJoin[TU](
    fromCast: UniformFanOutShape[GraphTrace[TU], GraphTrace[TU]],
    outCast: UniformFanOutShape[GraphTrace[TU], GraphTrace[TU]],
    fromJoin: UniformFanOutShape[Joined[TU], Joined[TU]],
    joinCast: UniformFanOutShape[Joined[TU], Joined[TU]],
    fromKey: String,
    toKey: String
  )(implicit builder: GraphDSL.Builder[Any]) = {
    val merge = builder.add(Merge[Joined[TU]](2))
    // to keep dependency counts clean, we'll just pass through both broadcasts
    fromCast ~> outCast ~> Flow[GraphTrace[TU]].mapConcat { _ => 
      List.empty[Joined[TU]]
    } ~> merge

    // remap directly from join stream
    fromJoin ~> Flow[Joined[TU]].map {
      case items => {
        items ++ items.get(fromKey).map(toKey -> _).toMap
      }
    } ~> merge

    merge ~> joinCast
  }

  type JoinOutput[TU] = (Option[Joined[TU]], Option[GraphTrace[TU]])
  private def linkJoinToNextWithLeftJoin[TU](
    joiner:   FanInShape2[(List[String], Joined[TU]), (List[String], GraphTrace[TU]), (List[String], JoinOutput[TU])],
    toKey:    String,
    joinCast: UniformFanOutShape[Joined[TU], Joined[TU]],
    pushExplain: ((String, JsObject)) => Unit,    
  )(implicit builder: GraphDSL.Builder[Any]) = {
    val partition = builder.add(Partition[(List[String], JoinOutput[TU])](2, {
      case (_, (_, Some(_))) => 0
      case (_, (_, None))    => 1
    }))
    joiner.out ~> partition.in
    partition.out(0) ~> Flow[(List[String], JoinOutput[TU])].map {
      case (k, (Some(joined), Some(graph))) => {
        val v = joined + (toKey -> graph)
        pushExplain("emit", Json.obj("key" -> k))
        v
      }
      case (k, _) => throw Errors.streamError("Invalid partition: right" + k)
    } ~> joinCast

    // separate left join stream
    val leftJoinCast = builder.add(Broadcast[Joined[TU]](1))
    partition.out(1) ~> Flow[(List[String], JoinOutput[TU])].map {
      case (k, (Some(joined), None)) => {
        //, "left" -> true
        pushExplain("emit", Json.obj("key" -> k))
        joined
      }
      case (k, _) => throw Errors.streamError("Invalid partition: left" + k)
    } ~> leftJoinCast

    toKey -> leftJoinCast
  }

  private def linkJoinToNext[TU](
    joiner:   FanInShape2[(List[String], Joined[TU]), (List[String], GraphTrace[TU]), (List[String], JoinOutput[TU])],
    toKey:    String,
    joinCast: UniformFanOutShape[Joined[TU], Joined[TU]],
    pushExplain: ((String, JsObject)) => Unit,    
  )(implicit builder: GraphDSL.Builder[Any]) = {
    joiner.out ~> Flow[(List[String], JoinOutput[TU])].map {
      case (k, (Some(joined), Some(graph))) => {
        val v = joined + (toKey -> graph)
        //, "left" -> false
        pushExplain("emit", Json.obj("key" -> k))
        v
      }
      case (k, _) => {
        throw Errors.streamError("Invalid join tuple " + k)
      }
    } ~> joinCast
  }

  /**
    * Final Merge
    * Joins all terminal broadcasts as well as left joins together
    */
sealed trait JoinUnit[TU] {
  def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], explain: RelationalQueryExplain): UniformFanOutShape[Joined[TU], Joined[TU]]

  val key: String

  val isLeft: Boolean
}

private case class JoinTerminus[TU](key: String, cast: UniformFanOutShape[Joined[TU], Joined[TU]], leftJoin: Boolean) extends JoinUnit[TU] {
  def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], explain: RelationalQueryExplain) = cast

  val isLeft = leftJoin
}

private case class JoinTree[TU](joinKey: String, a: JoinUnit[TU], b: JoinUnit[TU])(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]]) extends JoinUnit[TU] {

  val key = joinKey

  val isLeft = false

  def merge(query: RelationalQuery)(implicit builder: GraphDSL.Builder[Any], explain: RelationalQueryExplain) = {
    val getKey = { item: Joined[TU] =>
      val fromItem = item.getOrElse(joinKey, throw new Exception("invalid merged item"))
      tracing.joinKey(fromItem)
    }

    val isLeft = a.isLeft || b.isLeft

    val explainKey = isLeft match {
      case false => s"${a.key}<>${b.key}[${joinKey}]"
      case true  => s"${a.key}<>${b.key}[${joinKey}]:left"
    }

    val pushExplain = explain.pusher(explainKey)

    val out = builder.add(Broadcast[Joined[TU]](1))

    val nextJoin = buildMergeJoin(
      a.merge(query),
      b.merge(query),
      pushExplain,
      leftOuter = isLeft || query.shouldOuterJoin(b.key), // opposite, yeah it's weird
      rightOuter = isLeft || query.shouldOuterJoin(a.key))(
      getKey,
      getKey)

    nextJoin.out ~> Flow[(List[String], (Option[Joined[TU]], Option[Joined[TU]]))].map {
      case (k, (maybeA, maybeB)) => {
        pushExplain("emit", Json.obj("key" -> k))
        maybeA.getOrElse(Map()) ++ maybeB.getOrElse(Map())
      }
    } ~> out

    out
  }
}

  private def calculateFinalMerge[TU](
    query:                 RelationalQuery,
    joincastMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]],
    leftJoinMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]
  )(implicit builder: GraphDSL.Builder[Any], targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]], explain: RelationalQueryExplain) = {

    val joinTree = getFinalJoinTree(query, joincastMap, leftJoinMap)

    joinTree.merge(query)
  }

  /**
    * Algo:
    * 1. Start with leaves
    * 2. Get all leaves from common root. Lookup left joins to include with this
    */
  private def getLeaves[TU](
    traceQueries: List[KeyedQuery[TraceQuery]],
    joincastMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]    
  ): List[(String, JoinTerminus[TU])] = {
    val froms = traceQueries.map(_.query.fromName).toSet
    val traceMap = traceQueries.map { t => 
      t.key -> t.query.fromName
    }

    traceMap.filter { 
      case (k, _) => !froms.contains(k)
    }.map {
      case (k, f) => {
        f -> JoinTerminus(
          k, 
          joincastMap.getOrElse(k, throw new Exception(s"join ${k} not found")), 
          false
        )
      }
    }
  }

  private def getLeftJoinLeaves[TU](
    traceQueries: List[KeyedQuery[TraceQuery]],
    leftJoinMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]
  ): List[(String, JoinTerminus[TU])] = {
    traceQueries.filter(_.query.from.leftJoin).map { trace =>
      val k = trace.key
      val f = trace.query.fromName
      f -> JoinTerminus(
        k,
        leftJoinMap.getOrElse(k, throw new Exception(s"join ${k} not found")),
        true
      )
    }
  }

  /**
    * Join tree ordering: 
    * This accepts an ordering list that will determine the order of the outer joins
    * Outer joins will happen left to right
    *
    */
  private def getFinalJoinTree[TU](
    query: RelationalQuery,
    joincastMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]],
    leftJoinMap:           Map[String, UniformFanOutShape[Joined[TU], Joined[TU]]]
  )(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[GraphTrace[TU]]) = {
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

    val leftJoins = getLeftJoinLeaves(traceQueries, leftJoinMap).toList

    def buildLayer(rootKey: String, units: List[JoinUnit[TU]]): JoinUnit[TU] = {
      units match {
        case Nil => throw new Exception("0 unit layer")
        case head :: Nil => head
        case head :: second :: other => buildLayer(rootKey, JoinTree(rootKey, head, second) :: other)
      }
    }

    def collapseTree(current: List[(String, JoinUnit[TU])], collapsed: Set[String]): JoinUnit[TU] = {
      val baseLayer = current.groupBy(_._1).map {
        case (k, vs) => (k, vs.map(_._2).sortBy(i => ordering.indexOf(i.key))) // need to make sure follows TSort ordering
      }
      
      // find a current root where all leaves are collapsed
      val chooseRoot = baseLayer.find { 
        case (from, _) => {
          toLookup.getOrElse(from, Nil) match {
            case Nil => false
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
            collapsed + r
          )
        }
        case Some((r, toMerge)) => {
          // terminal
          buildLayer(r, toMerge)
        }
        case None => throw new Exception("unable to choose root and recurse")
      }
    }

    collapseTree(leaves ++ leftJoins, collapsed)
  }

  /**
   * MERGE JOINS
   */
  implicit val ordering: Ordering[List[String]] = Ordering.by { in: List[String] =>
    in.mkString("|")
  }

  private def buildMergeJoin[K, V1, V2](
    left:        UniformFanOutShape[V1, V1],
    right:       UniformFanOutShape[V2, V2],
    pushExplain: ((String, JsObject)) => Unit,
    leftOuter:   Boolean,
    rightOuter:  Boolean)(
    v1Key: V1 => K,
    v2Key: V2 => K)(implicit builder: GraphDSL.Builder[Any], ordering: Ordering[K], writes: Writes[K]) = {

    type LeftJoin = (K, V1)
    type RightJoin = (K, V2)

    val preJoin = builder.add(new Merge[Either[Either[LeftJoin, Unit], Either[RightJoin, Unit]]](2, eagerComplete = false))
    // val preJoin = builder.add(new ZipN[Either[Either[LeftJoin, Unit], Either[RightJoin, Unit]]](2))
    val joinBuffer = builder.add(new JoinBuffer[LeftJoin, RightJoin](pushExplain))
    val joiner = builder.add(new MergeJoin[K, V1, V2](pushExplain, leftOuter, rightOuter))

    // calculate keys
    left ~> Flow[V1].map {
      case joined => {
        val key = v1Key(joined)
        pushExplain("left-pre", Json.obj("key" -> key))
        pushExplain("buffer", Json.obj("action" -> "left-pre", "key" -> key))        
        Left(Left((key, joined)))
      }
    }.concat(Source(Left(Right(())) :: Nil)) ~> preJoin

    right ~> Flow[V2].map {
      case trace => {
        val key = v2Key(trace)
        pushExplain("right-pre", Json.obj("key" -> key))
        pushExplain("buffer", Json.obj("action" -> "right-pre", "key" -> key))
        Right(Left((key, trace)))
      }
    }.concat(Source(Right(Right(())) :: Nil)) ~> preJoin

    preJoin ~> joinBuffer.in

    joinBuffer.out0 ~> Flow[List[LeftJoin]].mapConcat(_.toList).map { i =>
      pushExplain("left", Json.obj("key" -> i._1))
      i
    } ~> joiner.in0
    joinBuffer.out1 ~> Flow[List[RightJoin]].mapConcat(_.toList).map { i =>
      val data = Json.obj("key" -> i._1)
      pushExplain("right", data)
      i
    } ~> joiner.in1
    joiner
  }
}
