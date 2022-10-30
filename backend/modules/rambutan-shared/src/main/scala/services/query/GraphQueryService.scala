package services

import models.{ IndexType, ESQuery, Errors, Sinks }
import models.query._
import models.graph._
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
import akka.stream.{ Attributes, FlowShape, OverflowStrategy }
import akka.stream.scaladsl.{ Source, Flow, Sink, GraphDSL, Broadcast, MergeSorted, Concat, Merge }
import GraphDSL.Implicits._
import models.index.NodeType
import scalaz.Alpha

private case class EdgeHop[T](obj: T, directedEdge: GraphEdgeType, edgeObj: JsObject)

// forceStop is used for initial emits
private case class GNFAHop[T](obj: T, state: Set[String], forceStop: Boolean) {
  def terminalTuple = {
    val terminalSegment = withFlag(state.contains(GNFA.End)) {
      (GNFAHop(obj, Set(GNFA.End), forceStop = false), true) :: Nil
    }

    val remainder = withFlag(!forceStop) {
      (GNFAHop(obj, state, forceStop = false), false) :: Nil
    }

    terminalSegment ++ remainder
  }
}

private object GNFAHop {
  def wrapTracing[T, TU](qt: QueryTracing[T, TU]) = {
    new QueryTracingReduced[GNFAHop[T], TU] {    
      def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
        qt.unitFromJs(js, edgeOverride)
      }

      def getId(unit: TU): String = {
        qt.getId(unit)
      }

      def getKey(unit: TU): String = {
        qt.getKey(unit)
      }

      def getTerminus(trace: GNFAHop[T]): TU = {
        qt.getTerminus(trace.obj)
      }
    }
  }
}

private object GNFA {
  val End = "End"

  // Turns a series of EdgeFollows into Generalized Non-deterministic Finite Automata
  // https://miro.com/app/board/uXjVPJGvNjA=/?share_link_id=310271569517
  def calculateLinear(
    follows: List[EdgeFollow]
  ) = {
    // do multiple passes
    // first pass for vertexes
    val zipped = follows.zipWithIndex.map {
      case (follow, idx) => {
        val fromVertex = s"v(${idx})"
        (follow, fromVertex)
      }
    }

    val zipReversed = zipped.reverse

    val (start, baseEdges) = zipReversed.foldLeft((End, List.empty[(String, EdgeTypeTraverse, String)])) {
      case ((toVertex, acc), (follow, fromVertex)) => {

        val next = follow.followType match {
          case FollowType.Optional => {
            follow.traverses.map { f =>
              (fromVertex, f, toVertex)
            }
          }
          case FollowType.Star => {
            follow.traverses.map { f =>
              (toVertex, f, toVertex)
            }
          }
          case FollowType.Target => {
            follow.traverses.map { f =>
              (fromVertex, f, toVertex)
            }
          }
        }
        // follow.followType
        (fromVertex, acc ++ next)
      }
    }

    // calculate it as a map
    val baseEdgeMap = baseEdges.groupBy(_._1).map {
      case (fromVertex, vs) => {
        fromVertex -> vs.map(v => (v._2, v._3)).toSet
      }
    }

    // just recalculate
    val reverseBaseEdgeMap = baseEdges.groupBy(_._3).map {
      case (toVertex, vs) => {
        toVertex -> vs.map(v => (v._2, v._1)).toSet
      }
    }


    def propagateEquivalence(
      acc: Map[String, Set[(EdgeTypeTraverse, String)]],
      fromVertex: String,
      toVertex: String
    ) = {
      // pass back toVertex edges to fromVertex
      val toVertexEdges = acc.getOrElse(toVertex, Set.empty[(EdgeTypeTraverse, String)])
      val fromVertexEdges = acc.getOrElse(fromVertex, Set.empty[(EdgeTypeTraverse, String)]) ++ toVertexEdges

      val edgePropMap = acc ++ Map(fromVertex -> fromVertexEdges)

      // any edges going into fromVertex, also need to go into toVertex
      val nodesToProp = reverseBaseEdgeMap.getOrElse(fromVertex, Set.empty[(EdgeTypeTraverse, String)])
      val nodePropMap = nodesToProp.toList.map {
        case (edgeTraverse, fromFromVertex) => {
          val propped = edgePropMap.getOrElse(fromFromVertex, Set.empty[(EdgeTypeTraverse, String)]) ++ Set((edgeTraverse, toVertex))
          fromFromVertex -> propped
        }
      }.toMap

      edgePropMap ++ nodePropMap
    }

    // handles degenerate case where start is equiv to initial
    def propEndEquivalence(equivToEnd: Set[String], fromVertex: String, toVertex: String) = {
      if (equivToEnd.contains(toVertex)) {
        equivToEnd + fromVertex
      } else {
        equivToEnd
      }
    }

    // second pass with equivalences
    val (_, flattenedMap, equivToEnd) = zipReversed.foldLeft((End, baseEdgeMap, Set(GNFA.End))) {
      case ((toVertex, acc, equivToEnd), (follow, fromVertex)) => {
        follow.followType match {
          case FollowType.Optional => {
            (fromVertex, propagateEquivalence(acc, fromVertex, toVertex), propEndEquivalence(equivToEnd, fromVertex, toVertex))
          }
          case FollowType.Star => {
            (fromVertex, propagateEquivalence(acc, fromVertex, toVertex), propEndEquivalence(equivToEnd, fromVertex, toVertex))
          }
          case FollowType.Target => {
            (fromVertex, acc, equivToEnd)
          }
        }
      }
    }

    val finalMap = flattenedMap.map {
      case (k, vs) => k -> vs.groupBy(_._1.edgeType)
    }

    (start, finalMap, equivToEnd.contains(start))
  }
}


@Singleton
class GraphQueryService @Inject() (
  configuration:        play.api.Configuration,
  nodeHydrationService: NodeHydrationService,
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  /**
   * Constants
   */
  val SearchScroll = 10000
  val NodeHopSize = 2000
  // val EdgeHopInputSize = 200
  val EdgeHopInputSize = 2000
  val RecursionSize = 10000
  val ExportHopSize = 10000

  /**
   *
   */
  def parseQuery(query: String): Either[fastparse.Parsed.TracedFailure, (Option[QueryTargetingRequest], GraphQuery)] = {
    fastparse.parse(query, GraphQuery.fullQuery(_)) match {
      case fastparse.Parsed.Success((maybeTargeting, query), _) => {
        Right((maybeTargeting, query))
      }
      case f: fastparse.Parsed.Failure => {
        Left(f.trace())
      }
    }
  }

  def runQuery(query: GraphQuery)(implicit targeting: QueryTargeting[TraceUnit], context: SpanContext) = {
    implicit val tracing = QueryTracing.Basic
    runQueryGeneric[GraphTrace[TraceUnit], TraceUnit, (String, GraphNode), QueryNode](query)
  }

  def runQueryGenericGraph(query: GraphQuery)(implicit targeting: QueryTargeting[GenericGraphUnit], context: SpanContext) = {
    implicit val tracing = QueryTracing.GenericGraph
    runQueryGeneric[GraphTrace[GenericGraphUnit], GenericGraphUnit, GenericGraphNode, GenericGraphNode](query)
  }

  private def runQueryGeneric[T, TU, IN, NO](query: GraphQuery)(
    implicit
    targeting:        QueryTargeting[TU],
    context:          SpanContext,
    tracing:          QueryTracing[T, TU],
    hasTraceKey:      HasTraceKey[TU],
    fileKeyExtractor: FileKeyExtractor[IN],
    flattener:        HydrationFlattener[T, TU],
    node:             HydrationMapper[TraceKey, JsObject, T, GraphTrace[IN]],
    code:             HydrationMapper[FileKey, (String, Array[String]), GraphTrace[IN], GraphTrace[NO]]): Future[(QueryResultHeader, Source[GraphTrace[NO], Any])] = {
    for {
      (sizeEstimate, _, traversed) <- executeUnit[T, TU](query, progressUpdates = false, cursor = None)
      rehydrated = context.withSpanS("graph.hydrate") { _ =>
        nodeHydrationService.rehydrate[T, TU, IN, NO](context.withSpanS("graph.query") { _ =>
          traversed
        })
      }
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

  /**
   * Raw queries
   */
  def executeUnit[T, TU](
    query:           GraphQuery,
    progressUpdates: Boolean,
    cursor:          Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Future[(Long, Source[Long, Any], Source[T, Any])] = {
    val nodeIndex = targeting.nodeIndexName
    val edgeIndex = targeting.edgeIndexName

    for {
      (rootSize, rootSource) <- rootSearch[T, TU](query.root, cursor)
      // elasticsearch caps out at 10000 when returning regular query so we do an explicit count
      size <- if (rootSize =?= 10000L && progressUpdates) {
        elasticSearchService.count(
          nodeIndex,
          targeting.rootQuery(query.root)) map (resp => (resp \ "count").as[Long])
      } else {
        Future.successful(rootSize)
      }
      (progressSource, adjustedSource) = if (progressUpdates) {
        // We don't care about dropping
        val (queue, queueSource) = Source.queue[Long](
          bufferSize = 20,
          OverflowStrategy.dropBuffer).preMaterialize()

        val newSource = rootSource.alsoTo {
          Flow[T].map { v =>
            tracing.getId(tracing.getTerminus(v))
          }.groupedWithin(2000, 600.milliseconds).scan((0L, "")) {
            case ((count, latestId), ids) => {
              val greater = ids.filter(_ > latestId)
              (count + greater.length, greater.maxByOption(i => i).getOrElse(latestId))
            }
          }.map {
            // should do mapAsync?
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
      traversed = adjustedSource.via {
        executeTrace(query.traverses)
      }
    } yield {
      (size, progressSource, traversed)
    }
  }

  /**
   * Assume sorted by root, sort by (root, dest)
   */
  // type TraceFlow[TU] = Flow[GraphTrace[TU], GraphTrace[TU], Any]
  private def sortBySecondary[T, TU](implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) = {
    sortBySecondaryGeneric[T, T, TU](i => i)
  }

  private def sortBySecondaryGeneric[V0, T, TU](f: V0 => T)(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]): Flow[V0, V0, _] = {
    // This is important!
    val secondaryOrdering = Ordering.by { a: V0 =>
      tracing.joinKey(f(a)).mkString("|")
    }

    val withTerminal = Flow[V0].map(Right.apply).concat(Source(Left(()) :: Nil))

    withTerminal.statefulMapConcat { () =>
      // Initialization is actually not useful?
      var collect = collection.mutable.ListBuffer.empty[V0]
      var currentRootId: Option[Vector[String]] = None

      {
        case Right(element) => {
          val rootId = tracing.sortKey(f(element))

          if (Option(rootId) =?= currentRootId) {
            collect += element
            Nil
          } else {
            //emit sorted
            val emit = collect.toList.sorted(secondaryOrdering)

            collect = collection.mutable.ListBuffer.empty[V0]
            collect += element

            currentRootId = Option(rootId)

            emit
          }
        }
        case Left(_) => {
          // final, emit
          val emit = collect.toList.sorted(secondaryOrdering)
          emit
        }
      }
    }
  }

  def executeTrace[T, TU](traverses: List[Traverse])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    context.withSpanF("query.graph.trace") { cc =>
      traverses match {
        case Nil => {
          Flow[T]
        }
        case _ => {
          val base = traverses.foldLeft(Flow[T]) {
            case (acc, t) => {
              acc.via {
                applyTraverse(t)(targeting, cc, tracing)
              }
            }
          }

          base.via(sortBySecondary)
        }
      }
    }
  }

  /**
   * To be exposed
   */
  private def rootSearch[T, TU](
    root:   GraphRoot,
    cursor: Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Future[(Long, Source[T, _])] = {

    val cc = context.decoupledSpan("query.graph.root")
    for {
      (size, source) <- elasticSearchService.source(
        targeting.nodeIndexName,
        targeting.rootQuery(root),
        sort = targeting.nodeSort,
        additional = cursor.map(_.searchAfter).getOrElse(Json.obj()),
        scrollSize = SearchScroll)
    } yield {
      (size, cc.terminateFor(source.map { i =>
        val unit = tracing.unitFromJs(i)
        tracing.newTrace(unit)
      }))
    }
  }

  private def applyTraverse[T, TU](
    traverse: Traverse)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {

    traverse match {
      case a: EdgeTraverse => {
        context.withSpanF("query.graph.trace.edge") { cc =>
          edgeTraverse(
            a.follow.traverses,
            a.target.traverses,
            a.typeHint,
            initial = true)(targeting, cc, tracing)
        }
      }
      case lin: LinearTraverse => {
        context.withSpanF("query.graph.trace.edge") { cc =>
          val (start, transitions, emitInitial) = GNFA.calculateLinear(
            lin.follows,
          )

          println("====================")

          transitions.toList.sortBy(_._1).foreach {
            case (k, vs) => {
              println("=========")
              println(k)
              println("=========")
              vs.foreach {
                case (t, ss) => println("  " + t + ":" + ss)
              }
            }
          }

          println("====================")

          Flow[T].map { i =>
            GNFAHop(i, Set(start), forceStop = false)
          }.via {
            gnfaTraverse(
              transitions,
              initial = true,
              emitInitial = emitInitial,
            )(targeting, cc, tracing)
          }.map(_.obj)
        }
      }
      case b: NodeTraverse => {
        context.withSpanF("query.graph.trace.node") { cc =>
          nodeTraverse(b.filters, b.follow.traverses)(targeting, cc, tracing)
        }
      }
      case ln: LinearNodeTraverse => {
        context.withSpanF("query.graph.trace.node") { cc =>
          val (start, transitions, emitInitial) = GNFA.calculateLinear(
            ln.follows,
          )

          Flow[T].map { i =>
            GNFAHop(i, Set(start), forceStop = false)
          }.via {
            gnfaTraverse(
              transitions,
              initial = true,
              emitInitial = emitInitial,
            )(targeting, cc, tracing)
          }.map(_.obj).via {
            nodeCheck(targeting.nodeIndexName, ln.filters).mapConcat {
              case ((trace, Some(obj)), true) => Some(trace)
              case _ => None
            }
          }
        }
      }
      case r: ReverseTraverse => {
        context.withSpanF("query.graph.trace.reverse") { cc =>
          reverseTraverse(r)(targeting, cc, tracing)
        }
      }
      case ren: RepeatedEdgeTraverseNew => {
        context.withSpanF("query.graph.trace.repeated") { cc =>
          repeatedEdgeTraverseNew(ren)(targeting, cc, tracing)
        }
      }
      case re: RepeatedEdgeTraverse[T, TU] => {
        // used for git
        context.withSpanF("query.graph.trace.repeated.legacy") { cc =>
          repeatedEdgeTraverse(re)(targeting, cc, tracing)
        }
      }
      // debug
      case c: OneHopTraverse => {
        context.withSpanF("query.graph.trace.hop") { cc =>
          onehopTraverse(c.follow, initial = true)(targeting, cc, tracing)
        }
      }
    }
  }

  /**
   * Helpers
   */
  private def nodeTraverse[T, TU](
    filters: List[NodeFilter],
    follow:  List[EdgeTypeTraverse])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    nodeTraverseInner(filters, follow).map(_._1)
  }

  private def nodeTraverseInner[T, TU](
    filters: List[NodeFilter],
    follow:  List[EdgeTypeTraverse])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, (T, Option[JsObject]), Any] = {

    val nodeIndex = targeting.nodeIndexName
    val edgeIndex = targeting.edgeIndexName

    // node hop should mark true /false
    val initialHop = nodeCheck(nodeIndex, filters)

    implicit val ordering = Ordering.by { a: (T, Option[JsObject]) =>
      tracing.sortKey(a._1).mkString("|")
    }

    initialHop.via {
      maybeRecurse { _ =>
        Flow[(T, Option[JsObject])]
          .map(_._1) // assert(_._2 =?= None)
          .via(onehopTraverse(follow, initial = false))
          .via(nodeTraverseInner(filters, follow))
      }
    }
  }

  private def repeatedEdgeTraverseNew[T, TU](
    traverse: RepeatedEdgeTraverseNew)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {

    implicit val ordering = tracing.ordering

    traverse.inner.foldLeft(Flow[T]) {
      case (flow, e) => flow.via {
        applyTraverse(e) // but also emit
      }
    }.mapConcat { trace =>
      // dangerous because this never terminates
      List(
        (trace, true),
        (trace, false))
    }.via {
      maybeRecurse { _ =>
        repeatedEdgeTraverseNew(traverse)
      }
    }
  }

  @deprecated
  private def repeatedEdgeTraverse[T, TU](
    traverse: RepeatedEdgeTraverse[T, TU])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    val follow = traverse.follow.traverses
    val edgeIndex = targeting.edgeIndexName

    implicit val ordering = tracing.ordering

    edgeHop(
      follow,
      nodeHint = None,
      initial = true // fix?
    ).mapConcat {
      case EdgeHop(trace, directedEdge, item) => {
        val isTerminal = traverse.shouldTerminate(trace)
        if (isTerminal) {
          (trace, true) :: Nil
        } else {
          List(
            (trace, true),
            (trace, false))
        }
      }
    }.via {
      maybeRecurse { _ =>
        repeatedEdgeTraverse(traverse)
      }
    }
  }

  private def gnfaTraverse[T, TU](
    transitionMap: Map[String, Map[GraphEdgeType, Set[(EdgeTypeTraverse, String)]]],
    initial: Boolean,
    emitInitial: Boolean
  )(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[GNFAHop[T], GNFAHop[T], _] = {

    implicit val ordering = Ordering.by { a: GNFAHop[T] =>
      tracing.sortKey(a.obj).mkString("|")
    }

    gnfaHop(
      transitionMap, 
      initial = initial,
      emitInitial = emitInitial
    ).mapConcat(_.terminalTuple).via {
      maybeRecurse { _ =>
        gnfaTraverse(
          transitionMap,
          initial = false,
          emitInitial = false,
        )
      }
    }
  }

  private def edgeTraverse[T, TU](
    follow:   List[EdgeTypeTraverse],
    target:   List[EdgeTypeTraverse],
    typeHint: Option[NodeType],
    initial:  Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {

    val edgeIndex = targeting.edgeIndexName

    implicit val ordering = tracing.ordering

    val allTraverses = follow ++ target
    val targetSet: Set[GraphEdgeType] = target.map(_.edgeType).toSet

    edgeHop(
      allTraverses,
      nodeHint = typeHint,
      initial = initial).map {
      case EdgeHop(trace, directedEdge, item) => {
        val isTerminal = targetSet.contains(directedEdge)
        (trace, isTerminal)
      }
    }.via {
      maybeRecurse { _ =>
        edgeTraverse(follow, target, typeHint, initial = false)
      }
    }
  }

  private def onehopTraverse[T, TU](
    follow:  List[EdgeTypeTraverse],
    initial: Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {

    edgeHop(
      follow,
      nodeHint = None,
      initial = initial) map {
      case EdgeHop(trace, directedEdge, item) => {
        trace
      }
    }
  }

  private def reverseTraverse[T, TU](traverse: ReverseTraverse)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    val traverses = traverse.traverses
    val initial = Flow[T].map(tracing.pushCopy) -> traverse.follow

    val (lastFlow, lastFollow) = traverses.reverse.foldLeft(initial) {
      case ((flow, prevFollow), EdgeTraverse(nextFollow, target, _)) => {
        val nextFlow = flow.via {
          edgeTraverse(
            prevFollow.traverses,
            target.reverse.traverses,
            typeHint = None,
            initial = false)
        }

        (nextFlow, nextFollow.reverse)
      }
      case ((flow, prevFollow), NodeTraverse(nextFollow, targets)) => {
        val nextFlow = flow.via {
          nodeTraverse(targets, prevFollow.traverses)
        }

        (nextFlow, nextFollow.reverse)
      }
      // We never reverse these
      case ((flow, prevFollow), RepeatedEdgeTraverseNew(inner)) => {
        val (nextFollow, remappedTraverse) = inner.reverse.foldLeft((prevFollow, List.empty[EdgeTraverse])) {
          case ((prev, acc), next) => {
            (next.follow, acc :+ EdgeTraverse(prev, next.target, None))
          }
        }

        val nextFlow = flow.via {
          repeatedEdgeTraverseNew(RepeatedEdgeTraverseNew(remappedTraverse))
        }

        (nextFlow, nextFollow)
      }
      case ((flow, prevFollow), RepeatedEdgeTraverse(_, _)) => {
        throw new Exception("invalid reversal: repeated.legacy")
      }
      case ((flow, prevFollow), ReverseTraverse(_, _)) => {
        throw new Exception("invalid reversal: reverse")
      }
      case ((flow, prevFollow), OneHopTraverse(_)) => {
        throw new Exception("invalid reversal: onehop")
      }
    }

    lastFlow
    // NOTE: node traverse is applied at SrcLog level
    // lastFlow.via {
    //   nodeTraverse(indexType, indexId, subQuery.root.filters.toList, lastFollow)
    // }
  }

  // Assumes you don't have two traverse of the same edgeType in different directions
  private def directionEdgeQuery[T, TU](
    edgeIndex: String,
    recursion: Boolean,
    traverses: List[EdgeTypeTraverse],
    traces:    List[T],
    nodeHint:  Option[NodeType])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracingReduced[T, TU]) = {
    val typeMap: Map[String, EdgeTypeTraverse] = traverses.map { i =>
      i.edgeType.edgeType.identifier -> i
    }.toMap

    val traceMap = traces.groupBy { trace =>
      tracing.getId(tracing.getTerminus(trace))
    } // id is unique so this is okay-ish
    val keys = traces.map(tracing.getTerminus).distinct

    // println("TRACEMAP")
    // traceMap.foreach(println)

    for {
      source <- ifNonEmpty(traverses) {
        context.withSpan(
          "query.graph.elasticsearch.initialize",
          "query.graph.recursion" -> recursion.toString(),
          "query.graph.count.input" -> keys.size.toString()) { cc =>
            // println(targeting.edgeQuery(traverses, keys, nodeHint))
            for {
              (cnt, src) <- elasticSearchService.source(
                edgeIndex,
                targeting.edgeQuery(traverses, keys, nodeHint),
                // sort is just for the scrolling
                // we need to resort later on
                sort = targeting.edgeSort,
                scrollSize = SearchScroll)
            } yield {
              cc.withSpanS(
                "query.graph.elasticsearch.consume",
                "query.graph.recursion" -> recursion.toString(),
                "query.graph.count.input" -> keys.size.toString(),
                "query.graph.count.output" -> cnt.toString()) { _ =>
                  src
                }
            }
          }
      }
    } yield {
      source.mapConcat { item =>
        // println(item)
        val edgeType = (item \ "_source" \ "type").as[String]
        // should never happen
        val directedEdge = typeMap.getOrElse(edgeType, throw Errors.streamError("invalid type")).edgeType
        val id = directedEdge.direction.extract(item)
        // println("CHECK TRACEMAP", id, traceMap.getOrElse(id, Nil))
        traceMap.getOrElse(id, Nil).map(trace => EdgeHop(trace, directedEdge, item))
      }
    }
  }

  private def nodeCheck[T, TU](
    nodeIndex: String,
    filters:   List[NodeFilter])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]) = {

    Flow[T].groupedWithin(NodeHopSize, 100.milliseconds).mapAsync(1) { traces =>
      val query = targeting.nodeQuery(traces.map(tracing.getTerminus).toList)

      for {
        (total, source) <- elasticSearchService.source(
          nodeIndex,
          ESQuery.bool(
            filter = ESQuery.bool(
              must = query :: filters.map(_.query)) :: Nil),
          sort = targeting.nodeSort,
          scrollSize = NodeHopSize)
        collectedSources <- context.withSpan(
          "query.graph.elasticsearch.node",
          "size" -> traces.size.toString()) { _ =>
            source.runWith(Sinks.ListAccum)
          }
        // assume ids are unique so that's all we need to check
        sourceMap = collectedSources.map { item =>
          (item \ "_source" \ "id").as[String] -> item
        }.toMap
      } yield {
        traces.map { item =>
          val id = tracing.getId(tracing.getTerminus(item))
          val graphNode = sourceMap.get(id)
          val newItem = graphNode match {
            case Some(gn) => {
              val newGraphNode = tracing.unitFromJs(gn)

              tracing.replaceHeadNode(item, id, newGraphNode)
            }
            case _ => item
          }

          // We can do a replace on item
          (
            (newItem, (graphNode.map(gn => (gn \ "_source").as[JsObject]))),
            graphNode.isDefined)
        }
      }
    }.mapConcat {
      s => s
    }
  }

  private def gnfaHop[T, TU](
    transitionMap: Map[String, Map[GraphEdgeType, Set[(EdgeTypeTraverse, String)]]],
    initial: Boolean,
    emitInitial: Boolean
  )(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[GNFAHop[T], GNFAHop[T], Any] = {

    val recursion = !initial

    val ordering = Ordering.by { a: GNFAHop[T] =>
      tracing.sortKey(a.obj).mkString("|")
    }

    implicit val wrappedTracing = GNFAHop.wrapTracing(tracing)

    Flow[GNFAHop[T]].groupedWithin(EdgeHopInputSize, 100.milliseconds).mapAsync(1) { traces =>
      // This duplicates traces as well
      val tracesByState = traces.flatMap { trace =>
        trace.state.map { s =>
          s -> trace
        }
      }.groupBy(_._1).map {
        case (k, vs) => k -> vs.map(_._2)
      }

      println("==========")
      val work = tracesByState.map {
        case (state, traces) => {

          val traverses = transitionMap.getOrElse(state, Map()).values.toList.flatten.map(_._1).distinct

          println("CHECK TMAP", state, traces.map { trace =>
            tracing.getId(tracing.getTerminus(trace.obj))
          }, traverses.map(_.edgeType.identifier).mkString(","))

          for {
            source <- ifNonEmpty(traverses) {
              directionEdgeQuery(
                targeting.edgeIndexName,
                recursion,
                traverses,
                traces.toList,
                nodeHint = None
              )
            }
            sliceResults <- source.runWith(Sinks.ListAccum[EdgeHop[GNFAHop[T]]])
          } yield {
            sliceResults
          }
        }
      }
      
      for {        
        allResults <- Future.sequence(work).map(_.flatten.toList)
        _ = println("==========")
        (cross, notCross) = allResults.partition { i =>
          i.directedEdge.crossFile
        }
        // TODO: this is ugly
        crossIds = cross.map { i =>
          i.directedEdge.direction.extractOpposite(i.edgeObj)
        }
        nodeSrc <- ifNonEmpty(crossIds) {
          elasticSearchService.source(
            targeting.nodeIndexName,
            ESQuery.termsSearch(
              "id",
              crossIds),
            List("id" -> "asc"),
            cross.length).map(_._2)
        }
        collectedNodes <- context.withSpan(
          "query.graph.elasticsearch.edgehop.node",
          "size" -> traces.size.toString()) { _ =>
            nodeSrc.runWith(Sinks.ListAccum)
          }    
        // assume ids are unique so that's all we need to check
        nodeMap = collectedNodes.map { item =>
          (item \ "_source" \ "id").as[String] -> item
        }.toMap
      } yield {
        val mappedResults = allResults.map {
          case EdgeHop(gnfaTrace, directedEdge, edgeObj) => {
            val baseTrace = tracing.traceHop(gnfaTrace.obj, directedEdge, edgeObj, initial)
            val id = directedEdge.direction.extractOpposite(edgeObj)
            val nextTrace = nodeMap.get(id) match {
              case Some(replaceNode) => {
                tracing.replaceHeadNode(baseTrace, id, tracing.unitFromJs(replaceNode))
              }
              case _ => baseTrace
            }

            // Recalculate state for GNFA
            val currentState = gnfaTrace.state

            val newState = currentState.toList.flatMap { s =>
              val innerMap = transitionMap.getOrElse(s, throw new Exception(s"invalid state ${s}"))

              // TODO: we should check name and index and stuff as well
              // discard instead of erroring out
              innerMap.get(directedEdge) match {
                case Some(i) => i.map(_._2)
                case _ => Nil
              }
            }.toSet

            GNFAHop(nextTrace, newState, forceStop = false)
          }
        }

        // allResults
        val allAllResults = (mappedResults ++ withFlag(emitInitial && initial) {
          traces.map(_.copy(state = Set(GNFA.End), forceStop = true))
        })
        
        val emit = allAllResults.sorted(ordering) // problem to sort after???

        emit.distinct.foreach { e =>
          println {
            tracing.iterateAll(e.obj).map(tracing.getId).mkString("->")
          }
          println("--" + e.state.mkString(","))
        }

        // TODO: is this efficient?
        emit.distinct
      }
    }.mapConcat(i => i)
  }

  // NOTE: the .trace outputted here is already hopped
  private def edgeHop[T, TU](
    edgeTraverses: List[EdgeTypeTraverse],
    nodeHint:      Option[NodeType],
    initial:       Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, EdgeHop[T], Any] = {

    val recursion = !initial

    val edgeHopOrdering = Ordering.by { a: EdgeHop[T] =>
      tracing.sortKey(a.obj).mkString("|")
    }

    Flow[T].groupedWithin(EdgeHopInputSize, 100.milliseconds).mapAsync(1) { traces =>
      val groupedTraverses = edgeTraverses.groupBy(_.edgeType.direction)

      for {
        // query will scramble the list
        source <- directionEdgeQuery(
          targeting.edgeIndexName,
          recursion,
          edgeTraverses,
          traces.toList,
          nodeHint)
        allResults <- source.runWith(models.Sinks.ListAccum[EdgeHop[T]])
        (cross, notCross) = allResults.partition { i =>
          i.directedEdge.crossFile
        }
        // TODO: this is ugly
        crossIds = cross.map { i =>
          i.directedEdge.direction.extractOpposite(i.edgeObj)
        }
        nodeSrc <- ifNonEmpty(crossIds) {
          elasticSearchService.source(
            targeting.nodeIndexName,
            ESQuery.termsSearch(
              "id",
              crossIds),
            List("id" -> "asc"),
            cross.length).map(_._2)
        }
        collectedNodes <- context.withSpan(
          "query.graph.elasticsearch.edgehop.node",
          "size" -> traces.size.toString()) { _ =>
            nodeSrc.runWith(Sinks.ListAccum)
          }
        // assume ids are unique so that's all we need to check
        nodeMap = collectedNodes.map { item =>
          (item \ "_source" \ "id").as[String] -> item
        }.toMap
      } yield {
        allResults.sorted(edgeHopOrdering).map {
          case EdgeHop(trace, directedEdge, edgeObj) => {
            val baseTrace = tracing.traceHop(trace, directedEdge, edgeObj, initial)
            val id = directedEdge.direction.extractOpposite(edgeObj)
            val nextTrace = nodeMap.get(id) match {
              case Some(replaceNode) => {
                tracing.replaceHeadNode(baseTrace, id, tracing.unitFromJs(replaceNode))
              }
              case _ => baseTrace
            }

            EdgeHop(nextTrace, directedEdge, edgeObj)
          }
        }
      }
    }.mapConcat(s => s)
  }  

  // this is fine to keep pretty big because this is just a pass through phase
  // only issue here is memory usage

  private def maybeRecurse[T](
    f: Unit => Flow[T, T, Any])(implicit ordering: Ordering[T]): Flow[(T, Boolean), T, Any] = {

    // big groupedWithin (10000), create two sources
    // edge hop is the one that slows down the querying
    Flow[(T, Boolean)].groupedWithin(RecursionSize, 100.milliseconds).flatMapConcat { items =>

      val grouped = items.groupBy(_._2)

      val terminal = grouped.getOrElse(true, Nil).map(_._1)
      val nonTerminal = grouped.getOrElse(false, Nil).map(_._1)

      def terminalSource = Source(terminal)
      def nonTerminalSource = Source(nonTerminal).via {
        f(())
      }

      (terminal.length, nonTerminal.length) match {
        case (_, 0) => terminalSource
        case (0, _) => nonTerminalSource
        case _ => {
          terminalSource.mergeSorted(nonTerminalSource)
        }
      }
    }
  }
}
