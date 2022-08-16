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

private case class StatefulTraverseUnwind[T, TU](
  node:  JsObject,
  trace: T,
  // teleport
  names: List[String],
  // unwind sequence
  unwindSequence: List[EdgeTypeTarget])

@Singleton
class GraphQueryService @Inject() (
  configuration:        play.api.Configuration,
  nodeHydrationService: NodeHydrationService,
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  /**
   * orderings
   */
  // TODO: this is super dangerous because conflicts with secondaryOrdering
  // implicit private val o1 = Ordering.by { a: GraphTrace[TraceUnit] =>
  //   a.sortKey.mkString("|")
  // }

  // for nodeCheck stuff

  /**
   * Constants
   */
  val SearchScroll = 10000
  val NodeHopSize = 2000
  val EdgeHopInputSize = 200
  val RecursionSize = 10000
  val ExportHopSize = 10000

  // ???
  val StatefulBatchSize = 200

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
    code:             HydrationMapper[FileKey, String, GraphTrace[IN], GraphTrace[NO]]): Future[(QueryResultHeader, Source[GraphTrace[NO], Any])] = {
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
            tracing.getId(v)
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
  private def sortBySecondaryStateful[T, TU](implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) = {
    sortBySecondaryGeneric[StatefulTraverseUnwind[T, TU], T, TU](_.trace)
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
      var currentRootId: Option[List[String]] = None

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
    traverses match {
      case Nil => {
        Flow[T]
      }
      case _ => {
        val base = traverses.foldLeft(Flow[T]) {
          case (acc, t) => {
            acc.via {
              applyTraverse(t)
            }
          }
        }

        base.via(sortBySecondary)
      }
    }
  }

  /**
   * To be exposed
   */
  private def rootSearch[T, TU](
    root:   GraphRoot,
    cursor: Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]): Future[(Long, Source[T, _])] = {
    for {
      (size, source) <- elasticSearchService.source(
        targeting.nodeIndexName,
        targeting.rootQuery(root),
        sort = targeting.nodeSort,
        additional = cursor.map(_.searchAfter).getOrElse(Json.obj()),
        scrollSize = SearchScroll)
    } yield {
      (size, source.map { i =>
        val unit = tracing.unitFromJs(i)
        tracing.newTrace(unit)
      })
    }
  }

  private def applyTraverse[T, TU](
    traverse: Traverse)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {

    traverse match {
      case a: EdgeTraverse => {
        edgeTraverse(
          a.follow.traverses,
          a.target.traverses,
          a.typeHint,
          initial = true)
      }
      case b: NodeTraverse => {
        nodeTraverse(b.filters, b.follow.traverses)
      }
      case r: ReverseTraverse => {
        reverseTraverse(r)
      }
      case s: StatefulTraverse => {
        statefulTraverse(s)
      }
      case re: RepeatedEdgeTraverse[T, TU] => {
        // used for git
        repeatedEdgeTraverse(re)
      }
      // debug
      case c: OneHopTraverse => {
        onehopTraverse(c.follow, initial = true)
      }
      case e: FilterTraverse => {
        filterTraverse(e.traverses)
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

  private def repeatedEdgeTraverse[T, TU](
    traverse: RepeatedEdgeTraverse[T, TU])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    val follow = traverse.follow.traverses
    val edgeIndex = targeting.edgeIndexName

    implicit val ordering = tracing.ordering

    edgeHop(
      follow,
      nodeHint = None,
      recursion = true // fix
    ).mapConcat {
      case EdgeHop(trace, directedEdge, item) => {
        val nextTrace = tracing.traceHop(trace, directedEdge, item, initial = true)

        val isTerminal = traverse.shouldTerminate(nextTrace)
        if (isTerminal) {
          (nextTrace, true) :: Nil
        } else {
          List(
            (nextTrace, true),
            (nextTrace, false))
        }
      }
    }.via {
      maybeRecurse { _ =>
        repeatedEdgeTraverse(traverse)
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
      recursion = !initial).map {
      case EdgeHop(trace, directedEdge, item) => {
        val nextTrace = tracing.traceHop(trace, directedEdge, item, initial)
        val isTerminal = targetSet.contains(directedEdge)
        (nextTrace, isTerminal)
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
      recursion = !initial) map {
      case EdgeHop(trace, directedEdge, item) => {
        tracing.traceHop(trace, directedEdge, item, initial)
      }
    }
  }

  private def filterTraverse[T, TU](traverses: List[Traverse])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    val dropRange = collection.immutable.Range(0, traverses.filter(_.isColumn).length)

    executeTrace(traverses).map {
      case i => dropRange.foldLeft(i) {
        case (acc, _) => tracing.dropHead(acc)
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
      case ((flow, prevFollow), FilterTraverse(f)) => {
        // filters do not need to be reversed
        // because we're already at the right node
        val nextFlow = flow.via {
          filterTraverse(f)
        }
        (nextFlow, new EdgeTypeFollow(Nil))
      }
      // Stateful traversals are reversed manually at the EdgePredicate level
      case ((flow, prevFollow), StatefulTraverse(_, _, _, _, _, _)) => {
        throw new Exception("invalid reversal: stateful")
      }
      // We never reverse these
      case ((flow, prevFollow), ReverseTraverse(_, _)) => {
        throw new Exception("invalid reversal: reverse")
      }
      case ((flow, prevFollow), OneHopTraverse(_)) => {
        throw new Exception("invalid reversal: onehop")
      }
    }

    lastFlow
    // lastFlow.via {
    //   nodeTraverse(indexType, indexId, subQuery.root.filters.toList, lastFollow)
    // }
  }

  private def statefulTraverse[T, TU](traverse: StatefulTraverse)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, T, _] = {
    Flow[T]
      .map(tracing.pushCopy)
      .via(performWind(traverse))
      .via(calculateUnwind(traverse))
      .via(performTeleport(traverse))
      .via(performUnwind(traverse.follow, traverse.target))
  }

  private def performWind[T, TU](traverse: StatefulTraverse)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]) = {
    val windTarget = NodeTypeFilter(traverse.from) :: Nil
    val windFollow = traverse.mapping.keySet.toList.map(EdgeTypeTraverse.basic)

    nodeTraverseInner[T, TU](
      follow = windFollow,
      filters = windTarget)
  }

  private def calculateUnwind[T, TU](traverse: StatefulTraverse)(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) = {
    Flow[(T, Option[JsObject])].mapConcat {
      case (trace, Some(node)) => {
        // assert(node.nodeType =?= traverse.from)

        // calculate unwind mappings
        val unwinds = tracing.calculateUnwindSequence(traverse, trace)

        val names = traverse.teleport.getNames(node)

        println("NAMES", names)

        StatefulTraverseUnwind[T, TU](
          node,
          trace,
          names,
          unwinds) :: Nil
      }
      case _ => Nil
    }
  }

  private def performTeleport[T, TU](traverse: StatefulTraverse)(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]): Flow[StatefulTraverseUnwind[T, TU], StatefulTraverseUnwind[T, TU], _] = {
    Flow[StatefulTraverseUnwind[T, TU]].groupedWithin(StatefulBatchSize, 100.milliseconds).mapAsync(1) { statefulTeleports =>
      val nodeIndex = targeting.nodeIndexName

      val typeQuery = ESQuery.termSearch("type", traverse.to.identifier) // TODO: assumes everything has type
      val allNames = statefulTeleports.flatMap(_.names).distinct
      val nameQuery = traverse.teleport.nameQuery(allNames.toList)

      println(typeQuery :: nameQuery)

      for {
        (total, source) <- elasticSearchService.source(
          nodeIndex,
          ESQuery.bool(
            filter = ESQuery.bool(
              must = typeQuery :: nameQuery) :: Nil),
          sort = targeting.nodeSort,
          scrollSize = NodeHopSize)
        collectedSources <- source.runWith(Sinks.ListAccum)
        collectedMap = {
          collectedSources.flatMap { node =>
            traverse.teleport.getNames((node \ "_source").as[JsObject]).map(_ -> node)
          }.groupBy(_._1).map {
            case (k, vs) => k -> vs.map(_._2)
          }
        }
      } yield {
        // List[T]
        for {
          s <- statefulTeleports.toList
          node <- traverse.teleport.doJoin(s.node, s.names, collectedMap)
        } yield {
          // TODO: Janky
          val unit: TU = tracing.unitFromJs(
            node,
            edgeOverride = Some(GraphEdgeTypeTeleport.teleportTo(traverse.to)))
          val res: StatefulTraverseUnwind[T, TU] = s.copy(
            trace = tracing.injectNew(s.trace, unit))
          res
        }
      }
    }.mapConcat(i => i).via(sortBySecondaryStateful)
  }

  // Key assumption is that these are generally quite small
  private def performUnwind[T, TU](follows: List[GraphEdgeType], targets: List[GraphEdgeType])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]) = {
    Flow[StatefulTraverseUnwind[T, TU]].groupedWithin(StatefulBatchSize, 100.milliseconds).flatMapConcat { statefulTeleports =>

      // group into distinct unwinds
      val grouped = statefulTeleports.groupBy { ss =>
        ss.unwindSequence.map(_.traverses.map(_.edgeType).toSet)
      }

      // we do need to resort everything
      val allTraverseGroups = grouped.map {
        case (Nil, v) => {
          // special case empty
          Source(v.map(_.trace))
        }
        case (k, v) => {
          val allFilters = {
            v.map(_.unwindSequence.map(_.traverses.toVector.flatMap(_.filter).distinct.headOption))
          }

          val size = allFilters.map(_.length).max
          assert(allFilters.forall(_.length =?= size))
          assert(size =?= k.length)

          val traverseSequence = (0 to (size - 1)).map { i =>

            val filters = allFilters.flatMap(f => f(i))
            val names = filters.toList.flatMap {
              case EdgeNameFilter(name) => Some(name)
              case _                    => None
            }
            val indexes = filters.toList.flatMap {
              case EdgeIndexFilter(idx) => Some(idx)
              case _                    => None
            }
            val edgeTypeTraverses = k(i).toList.map { kk =>
              EdgeTypeTraverse(kk, Some(MultiEdgeFilter(names, indexes)))
            }

            EdgeTraverse(
              follow = EdgeTypeFollow(follows.map(EdgeTypeTraverse.basic)),
              target = EdgeTypeTarget(edgeTypeTraverses))
          } ++ List(
            EdgeTraverse(
              follow = EdgeTypeFollow(follows.map(EdgeTypeTraverse.basic)),
              target = EdgeTypeTarget(targets.map(EdgeTypeTraverse.basic))))

          traverseSequence.foldLeft(Source(v.map(_.trace))) {
            case (acc, next) => acc.via(applyTraverse(next))
          }
        }
      }

      allTraverseGroups.foldLeft(Source.empty[T]) {
        case (acc, next) => {
          acc.mergeSorted(next)(tracing.ordering)
        }
      }
    } // do we need to sort?
  }

  // Assumes traverses are all same direction
  private def directionEdgeQuery[T, TU](
    edgeIndex: String,
    recursion: Boolean,
    traverses: List[EdgeTypeTraverse],
    traces:    List[T],
    nodeHint:  Option[NodeType])(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]) = {
    val typeMap: Map[String, EdgeTypeTraverse] = traverses.map { i =>
      i.edgeType.edgeType.identifier -> i
    }.toMap

    val traceMap = traces.groupBy { trace =>
      tracing.getId(trace)
    } // id is unique so this is okay-ish
    val keys = traces.map(tracing.getTraceKey).distinct

    for {
      source <- ifNonEmpty(traverses) {
        context.withSpan(
          "query.graph.elasticsearch.initialize",
          "query.graph.recursion" -> recursion.toString(),
          "query.graph.count.input" -> keys.size.toString()) { cc =>
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
        val edgeType = (item \ "_source" \ "type").as[String]
        // should never happen
        val directedEdge = typeMap.getOrElse(edgeType, throw Errors.streamError("invalid type")).edgeType
        val id = directedEdge.direction.extract(item)
        traceMap.getOrElse(id, Nil).map(trace => EdgeHop(trace, directedEdge, item))
      }
    }
  }

  private def nodeCheck[T, TU](
    nodeIndex: String,
    filters:   List[NodeFilter])(implicit targeting: QueryTargeting[TU], tracing: QueryTracing[T, TU]) = {
    Flow[T].groupedWithin(NodeHopSize, 100.milliseconds).mapAsync(1) { traces =>
      val query = targeting.nodeQuery(traces.map(tracing.getTraceKey).toList)

      for {
        (total, source) <- elasticSearchService.source(
          nodeIndex,
          ESQuery.bool(
            filter = ESQuery.bool(
              must = query :: filters.map(_.query)) :: Nil),
          sort = targeting.nodeSort,
          scrollSize = NodeHopSize)
        collectedSources <- source.runWith(Sinks.ListAccum)
        // assume ids are unique so that's all we need to check
        sourceMap = collectedSources.map { item =>
          (item \ "_source" \ "id").as[String] -> (item \ "_source").as[JsObject]
        }.toMap
      } yield {
        traces.map { item =>
          val id = tracing.getId(item)
          val graphNode = sourceMap.get(id)
          ((item, graphNode), graphNode.isDefined)
        }
      }
    }.mapConcat {
      s => s
    }
  }

  private def edgeHop[T, TU](
    edgeTraverses: List[EdgeTypeTraverse],
    nodeHint:      Option[NodeType],
    recursion:     Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext, tracing: QueryTracing[T, TU]): Flow[T, EdgeHop[T], Any] = {

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
      } yield {
        allResults.sorted(edgeHopOrdering)
      }
    }.mapConcat(s => s)
  }

  // private type TraceTerminus[TU] = (GraphTrace[TU], Boolean)

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
