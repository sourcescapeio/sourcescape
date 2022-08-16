// package services.gq6

// import services._
// import models.{ IndexType, ESQuery, Errors, Sinks }
// import models.query._
// import models.graph._
// import models.index.{ NodeType, GraphNode }
// import silvousplay.imports._
// import silvousplay.api.SpanContext
// import play.api.libs.json._
// import akka.stream.scaladsl.{ Source, Flow, Sink, GraphDSL, Broadcast, MergeSorted, Concat, Merge }
// import akka.stream.{ Attributes, FlowShape, OverflowStrategy }
// import scala.concurrent.{ ExecutionContext, Future }
// import scala.concurrent.duration._
// import javax.inject._

// @Singleton
// class GraphQueryService @Inject() (
//   configuration:        play.api.Configuration,
//   nodeHydrationService: NodeHydrationService,
//   elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

//   /**
//    * Constants
//    */
//   val SearchScroll = 10000
//   val EdgeHopInputSize = 200
//   val RecursionSize = 10000

//   def runQuery(query: GraphQuery)(implicit targeting: QueryTargeting[TraceUnit], context: SpanContext) = {
//     runQueryGeneric[TraceUnit, (String, GraphNode), QueryNode](query)
//   }

//   private def runQueryGeneric[TU, IN, NO](query: GraphQuery)(
//     implicit
//     targeting:        QueryTargeting[TU],
//     context:          SpanContext,
//     hasTraceKey:      HasTraceKey[TU],
//     // for hydration
//     fileKeyExtractor: FileKeyExtractor[IN],
//     node:             HydrationMapper[TraceKey, JsObject, GraphTrace[TU], GraphTrace[IN]],
//     code:             HydrationMapper[FileKey, String, GraphTrace[IN], GraphTrace[NO]]): Future[(QueryResultHeader, Source[GraphTrace[NO], Any])] = {
//     for {
//       (sizeEstimate, _, traversed) <- executeUnit[TU](query, progressUpdates = false, cursor = None)(targeting, context)
//       rehydrated = context.withSpanS("graph.hydrate") { _ =>
//         nodeHydrationService.rehydrate[TU, IN, NO](context.withSpanS("graph.query") { _ =>
//           traversed
//         })
//       }
//       traceColumns = query.traverses.filter(_.isColumn).zipWithIndex.map {
//         case (_, idx) => QueryColumnDefinition(
//           s"trace_${idx}",
//           QueryResultType.NodeTrace)
//       }
//       header = QueryResultHeader(
//         isDiff = false,
//         sizeEstimate = sizeEstimate,
//         columns = traceColumns ++ List(
//           QueryColumnDefinition(
//             "terminus",
//             QueryResultType.NodeTrace)))
//     } yield {
//       (header, rehydrated)
//     }
//   }

//   def executeUnit[TU](
//     query:           GraphQuery,
//     progressUpdates: Boolean,
//     cursor:          Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU], context: SpanContext): Future[(Long, Source[Long, Any], Source[GraphTrace[TU], Any])] = {

//     val nodeIndex = targeting.nodeIndexName
//     val edgeIndex = targeting.edgeIndexName

//     for {
//       (rootSize, rootSource) <- rootSearch[TU](query.root, cursor)
//       // elasticsearch caps out at 10000 when returning regular query so we do an explicit count
//       size <- if (rootSize =?= 10000L && progressUpdates) {
//         elasticSearchService.count(
//           nodeIndex,
//           targeting.rootQuery(query.root)) map (resp => (resp \ "count").as[Long])
//       } else {
//         Future.successful(rootSize)
//       }
//       (progressSource, adjustedSource) = if (progressUpdates) {
//         // We don't care about dropping progress updates
//         val (queue, queueSource) = Source.queue[Long](
//           bufferSize = 20,
//           OverflowStrategy.dropBuffer).preMaterialize()

//         val newSource = rootSource.alsoTo {
//           Flow[GraphTrace[TU]].map { v =>
//             targeting.getId(v.terminusId)
//           }.groupedWithin(2000, 600.milliseconds).scan((0L, "")) {
//             case ((count, latestId), ids) => {
//               val greater = ids.filter(_ > latestId)
//               (count + greater.length, greater.maxByOption(i => i).getOrElse(latestId))
//             }
//           }.mapAsync(1) {
//             // so it's all sequenced
//             case (count, _) => {
//               queue.offer(count)
//             }
//           }.to(
//             Sink.onComplete({ _ =>
//               queue.complete()
//             }))
//         }

//         (queueSource, newSource)
//       } else {
//         (Source[Long](Nil), rootSource)
//       }

//       // do edge traverse
//       traversed = adjustedSource.via {
//         executeTrace(query.traverses)
//       }
//     } yield {
//       (size, progressSource, traversed)
//     }
//   }

//   /**
//    * Root
//    */
//   private def rootSearch[TU](
//     root:   GraphRoot,
//     cursor: Option[RelationalKeyItem])(implicit targeting: QueryTargeting[TU]): Future[(Long, Source[GraphTrace[TU], _])] = {
//     for {
//       (size, source) <- elasticSearchService.source(
//         targeting.nodeIndexName,
//         targeting.rootQuery(root),
//         sort = targeting.nodeSort,
//         additional = cursor.map(_.searchAfter).getOrElse(Json.obj()),
//         scrollSize = SearchScroll)
//     } yield {
//       (size, source.map { i =>
//         val traceUnit = targeting.unitFromJs(i)
//         GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, traceUnit))
//       })
//     }
//   }

//   /**
//    * Tracing
//    */
//   def executeTrace[TU](traverses: List[Traverse])(implicit targeting: QueryTargeting[TU], context: SpanContext): TraceFlow[TU] = {
//     traverses match {
//       case Nil => {
//         Flow[GraphTrace[TU]]
//       }
//       case _ => {
//         val base = traverses.foldLeft(Flow[GraphTrace[TU]]) {
//           case (acc, t) => {
//             acc.via {
//               applyTraverse(t)
//             }
//           }
//         }

//         base.via(sortBySecondary)
//       }
//     }
//   }

//   private def applyTraverse[TU](
//     traverse: Traverse)(implicit targeting: QueryTargeting[TU], context: SpanContext): TraceFlow[TU] = {

//     traverse match {
//       case a: EdgeTraverse => {
//         edgeTraverse(
//           a.follow.traverses,
//           a.target.traverses,
//           a.typeHint,
//           initial = true)
//       }
//       // case b: NodeTraverse => {
//       //   nodeTraverse(b.filters, b.follow.traverses)
//       // }
//       // case r: ReverseTraverse => {
//       //   reverseTraverse(r)
//       // }
//       // case s: StatefulTraverse => {
//       //   statefulTraverse(s)
//       // }
//       // case re: RepeatedEdgeTraverse[TU] => {
//       //   // used for git
//       //   repeatedEdgeTraverse(re)
//       // }
//       // // debug
//       // case c: OneHopTraverse => {
//       //   onehopTraverse(c.follow, initial = true)
//       // }
//       // case e: FilterTraverse => {
//       //   filterTraverse(e.traverses)
//       // }
//     }
//   }

//   /**
//    * Edge Traverse
//    */
//   private def edgeTraverse[TU](
//     follow:   List[EdgeTypeTraverse],
//     target:   List[EdgeTypeTraverse],
//     typeHint: Option[NodeType],
//     initial:  Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext): TraceFlow[TU] = {

//     val edgeIndex = targeting.edgeIndexName

//     implicit val ordering = targeting.ordering

//     val allTraverses = follow ++ target
//     val targetSet: Set[GraphEdgeType] = target.map(_.edgeType).toSet

//     edgeHop(
//       allTraverses,
//       nodeHint = typeHint,
//       recursion = !initial).map {
//       case EdgeHop(trace, directedEdge, item) => {
//         val nextUnit = targeting.traceHop(trace.terminusId, directedEdge, item)
//         val nextTrace = if (initial) {
//           trace.injectNew(nextUnit)
//         } else {
//           trace.injectHead(nextUnit)
//         }

//         val isTerminal = targetSet.contains(directedEdge)
//         (nextTrace, isTerminal)
//       }
//     }.via {
//       maybeRecurse { _ =>
//         edgeTraverse(follow, target, typeHint, initial = false)
//       }
//     }
//   }

//   private def edgeHop[TU](
//     edgeTraverses: List[EdgeTypeTraverse],
//     nodeHint:      Option[NodeType],
//     recursion:     Boolean)(implicit targeting: QueryTargeting[TU], context: SpanContext): Flow[GraphTrace[TU], EdgeHop[GraphTrace[TU]], Any] = {

//     val edgeHopOrdering = Ordering.by { a: EdgeHop[GraphTrace[TU]] =>
//       a.obj.sortKey.mkString("|")
//     }

//     Flow[GraphTrace[TU]].groupedWithin(EdgeHopInputSize, 100.milliseconds).mapAsync(1) { traces =>
//       val groupedTraverses = edgeTraverses.groupBy(_.edgeType.direction)

//       for {
//         // query will scramble the list
//         source <- directionEdgeQuery(
//           targeting.edgeIndexName,
//           recursion,
//           edgeTraverses,
//           traces.toList,
//           nodeHint)
//         allResults <- source.runWith(models.Sinks.ListAccum[EdgeHop[GraphTrace[TU]]])
//       } yield {
//         allResults.sorted(edgeHopOrdering)
//       }
//     }.mapConcat(s => s)
//   }

//   private def directionEdgeQuery[T, TU](
//     edgeIndex: String,
//     recursion: Boolean,
//     traverses: List[EdgeTypeTraverse],
//     traces:    List[GraphTrace[TU]],
//     nodeHint:  Option[NodeType])(implicit targeting: QueryTargeting[TU], context: SpanContext) = {
//     val typeMap: Map[String, EdgeTypeTraverse] = traverses.map { i =>
//       i.edgeType.edgeType.identifier -> i
//     }.toMap

//     val traceMap = traces.groupBy { trace =>
//       targeting.getId(trace.terminusId)
//     } // id is unique so this is okay-ish
//     val keys = traces.map(_.terminusId).distinct

//     for {
//       source <- ifNonEmpty(traverses) {
//         context.withSpan(
//           "query.graph.elasticsearch.initialize",
//           "query.graph.recursion" -> recursion.toString(),
//           "query.graph.count.input" -> keys.size.toString()) { cc =>
//             for {
//               (cnt, src) <- elasticSearchService.source(
//                 edgeIndex,
//                 targeting.edgeQuery(traverses, keys, nodeHint),
//                 // sort is just for the scrolling
//                 // we need to resort later on
//                 sort = targeting.edgeSort,
//                 scrollSize = SearchScroll)
//             } yield {
//               cc.withSpanS(
//                 "query.graph.elasticsearch.consume",
//                 "query.graph.recursion" -> recursion.toString(),
//                 "query.graph.count.input" -> keys.size.toString(),
//                 "query.graph.count.output" -> cnt.toString()) { _ =>
//                   src
//                 }
//             }
//           }
//       }
//     } yield {
//       source.mapConcat { item =>
//         val edgeType = (item \ "_source" \ "type").as[String]
//         // should never happen
//         val directedEdge = typeMap.getOrElse(edgeType, throw Errors.streamError("invalid type")).edgeType
//         val id = directedEdge.direction.extract(item)
//         traceMap.getOrElse(id, Nil).map(trace => EdgeHop(trace, directedEdge, item))
//       }
//     }
//   }

//   private def maybeRecurse[T](
//     f: Unit => Flow[T, T, Any])(implicit ordering: Ordering[T]): Flow[(T, Boolean), T, Any] = {

//     // big groupedWithin (10000), create two sources
//     // edge hop is the one that slows down the querying
//     Flow[(T, Boolean)].groupedWithin(RecursionSize, 100.milliseconds).flatMapConcat { items =>

//       val grouped = items.groupBy(_._2)

//       val terminal = grouped.getOrElse(true, Nil).map(_._1)
//       val nonTerminal = grouped.getOrElse(false, Nil).map(_._1)

//       def terminalSource = Source(terminal)
//       def nonTerminalSource = Source(nonTerminal).via {
//         f(())
//       }

//       (terminal.length, nonTerminal.length) match {
//         case (_, 0) => terminalSource
//         case (0, _) => nonTerminalSource
//         case _ => {
//           terminalSource.mergeSorted(nonTerminalSource)
//         }
//       }
//     }
//   }

//   /**
//    * Sorts
//    */
//   type TraceFlow[TU] = Flow[GraphTrace[TU], GraphTrace[TU], Any]
//   private def sortBySecondary[TU](implicit targeting: QueryTargeting[TU]) = {
//     sortBySecondaryGeneric[GraphTrace[TU], TU](i => i)
//   }
//   private def sortBySecondaryStateful[TU](implicit targeting: QueryTargeting[TU]) = {
//     sortBySecondaryGeneric[StatefulTraverseUnwind[TU], TU](_.trace)
//   }
//   private def sortBySecondaryGeneric[T, TU](f: T => GraphTrace[TU])(implicit targeting: QueryTargeting[TU]) = {
//     // This is important!
//     val secondaryOrdering = Ordering.by { a: T =>
//       f(a).joinKey.mkString("|")
//     }

//     val withTerminal = Flow[T].map(Right.apply).concat(Source(Left(()) :: Nil))

//     withTerminal.statefulMapConcat { () =>
//       // Initialization is actually not useful?
//       var collect = collection.mutable.ListBuffer.empty[T]
//       var currentRootId: Option[List[String]] = None

//       {
//         case Right(element) => {
//           val rootId = f(element).sortKey

//           if (Option(rootId) =?= currentRootId) {
//             collect += element
//             Nil
//           } else {
//             //emit sorted
//             val emit = collect.toList.sorted(secondaryOrdering)

//             collect = collection.mutable.ListBuffer.empty[T]
//             collect += element

//             currentRootId = Option(rootId)

//             emit
//           }
//         }
//         case Left(_) => {
//           // final, emit
//           val emit = collect.toList.sorted(secondaryOrdering)
//           emit
//         }
//       }
//     }
//   }

// }