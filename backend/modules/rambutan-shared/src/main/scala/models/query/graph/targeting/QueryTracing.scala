package models.query

import play.api.libs.json._
import silvousplay.imports._

// Actual targeting object
trait QueryTracing[T, TU] {
  self =>

  val extractor: HasBasicExtraction[T]

  def traceHop(unit: T, edgeType: GraphEdgeType, edgeJs: JsObject, initial: Boolean): T

  /**
   * Inherited
   */
  // HasBasicExtractor
  def getId(unit: T) = extractor.getId(unit)

  def getKey(unit: T) = extractor.getKey(unit)

  def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None): TU

  def getTraceKey(trace: T): TU

  def pushExternalKey(trace: T): T

  def pushCopy(trace: T): T

  def dropHead(trace: T): T

  def injectNew(trace: T, unit: TU): T

  def injectHead(trace: T, unit: TU): T

  def newTrace(unit: TU): T

  /**
   * Sorting stuff
   */
  def sortKey(trace: T): List[String]

  final def joinKey(trace: T): List[String] = sortKey(trace) :+ headKey(trace)

  final def headKey(trace: T): String = getKey(trace)

  def ordering: Ordering[T]

  /**
   * Unwind
   */
  def calculateUnwindSequence(traverse: StatefulTraverse, trace: T): List[EdgeTypeTarget]
}

object QueryTracing {
  case object GenericGraph extends QueryTracing[GraphTrace[GenericGraphUnit], GenericGraphUnit] {
    val extractor = new HasBasicExtraction[GraphTrace[GenericGraphUnit]] {
      // just gets basic id
      def getId(trace: GraphTrace[GenericGraphUnit]): String = {
        trace.terminusId.id
      }

      // used to compute join keys for relational
      def getKey(trace: GraphTrace[GenericGraphUnit]): String = {
        val unit = trace.terminusId
        s"${unit.orgId}/${unit.id}"
      }
    }

    def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
      val id = (js \ "_source" \ "id").as[String]
      val orgId = (js \ "_source" \ "org_id").as[String]

      GenericGraphUnit(
        edgeOverride,
        orgId,
        id)
    }

    def newTrace(unit: GenericGraphUnit) = {
      GraphTrace(Nil, Nil, SubTrace(Nil, unit))
    }

    def getTraceKey(trace: GraphTrace[GenericGraphUnit]): GenericGraphUnit = {
      trace.terminusId
    }

    def pushCopy(trace: GraphTrace[GenericGraphUnit]): GraphTrace[GenericGraphUnit] = {
      trace.pushCopy
    }

    def dropHead(trace: GraphTrace[GenericGraphUnit]): GraphTrace[GenericGraphUnit] = {
      trace.dropHead
    }

    def pushExternalKey(trace: GraphTrace[GenericGraphUnit]) = trace.copy(
      externalKeys = trace.externalKeys :+ getKey(GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, trace.root))),
      tracesInternal = Nil,
      terminus = trace.terminus.wipe)

    def traceHop(trace: GraphTrace[GenericGraphUnit], edgeType: GraphEdgeType, edgeJs: JsObject, initial: Boolean): GraphTrace[GenericGraphUnit] = {
      val oppositeId = edgeType.direction.extractOpposite(edgeJs)

      val nextUnit = trace.terminusId.copy(
        edgeType = Some(edgeType),
        id = oppositeId)

      if (initial) {
        injectNew(trace, nextUnit)
      } else {
        injectHead(trace, nextUnit)
      }
    }

    def injectNew(trace: GraphTrace[GenericGraphUnit], unit: GenericGraphUnit) = {
      trace.injectNew(unit)
    }

    def injectHead(trace: GraphTrace[GenericGraphUnit], unit: GenericGraphUnit) = {
      trace.injectHead(unit)
    }

    def sortKey(trace: GraphTrace[GenericGraphUnit]): List[String] = {
      trace.externalKeys :+ getKey(GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, trace.root)))
    }

    def ordering = {
      Ordering.by { a: GraphTrace[GenericGraphUnit] =>
        sortKey(a).mkString("|")
      }
    }

    def calculateUnwindSequence(traverse: StatefulTraverse, trace: GraphTrace[GenericGraphUnit]) = {
      // Not supported
      List.empty[EdgeTypeTarget]
    }
  }

  case object Basic extends QueryTracing[GraphTrace[TraceUnit], TraceUnit] {
    val extractor = new HasBasicExtraction[GraphTrace[TraceUnit]] {
      def getId(trace: GraphTrace[TraceUnit]) = trace.terminusId.id

      def getKey(trace: GraphTrace[TraceUnit]) = {
        val unit = trace.terminusId
        s"${unit.key}/${unit.path}/${unit.id}"
      }
    }

    def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
      val key = (js \ "_source" \ "key").as[String]
      val path = (js \ "_source" \ "path").as[String]
      val id = (js \ "_source" \ "id").as[String]
      val name = (js \ "_source" \ "name").asOpt[String]
      val index = (js \ "_source" \ "index").asOpt[Int]

      TraceUnit(edgeOverride, key, path, id, name, index)
    }

    def getTraceKey(trace: GraphTrace[TraceUnit]): TraceUnit = {
      trace.terminusId
    }

    def pushCopy(trace: GraphTrace[TraceUnit]): GraphTrace[TraceUnit] = {
      trace.pushCopy
    }

    def dropHead(trace: GraphTrace[TraceUnit]): GraphTrace[TraceUnit] = {
      trace.dropHead
    }

    def injectNew(trace: GraphTrace[TraceUnit], unit: TraceUnit) = {
      trace.injectNew(unit)
    }

    def injectHead(trace: GraphTrace[TraceUnit], unit: TraceUnit) = {
      trace.injectHead(unit)
    }

    def newTrace(unit: TraceUnit) = {
      GraphTrace(Nil, Nil, SubTrace(Nil, unit))
    }

    def pushExternalKey(trace: GraphTrace[TraceUnit]) = trace.copy(
      externalKeys = trace.externalKeys :+ getKey(GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, trace.root))),
      tracesInternal = Nil,
      terminus = trace.terminus.wipe)

    def traceHop(trace: GraphTrace[TraceUnit], edgeType: GraphEdgeType, edgeJs: JsObject, initial: Boolean) = {
      val oppositeId = edgeType.direction.extractOpposite(edgeJs)

      val nextUnit = trace.terminusId.copy(
        edgeType = Some(edgeType),
        name = (edgeJs \ "_source" \ "name").asOpt[String],
        index = (edgeJs \ "_source" \ "index").asOpt[Int],
        id = oppositeId)

      if (initial) {
        injectNew(trace, nextUnit)
      } else {
        injectHead(trace, nextUnit)
      }
    }

    def sortKey(trace: GraphTrace[TraceUnit]): List[String] = {
      trace.externalKeys :+ getKey(GraphTrace(externalKeys = Nil, Nil, SubTrace(Nil, trace.root)))
    }

    def ordering = {
      Ordering.by { a: GraphTrace[TraceUnit] =>
        sortKey(a).mkString("|")
      }
    }

    def calculateUnwindSequence(traverse: StatefulTraverse, trace: GraphTrace[TraceUnit]): List[EdgeTypeTarget] = {
      (trace.terminus.tracesInternal ++ List(trace.terminusId)).flatMap { e =>
        // Option[T]
        for {
          edgeType <- e.edgeType
          targets <- traverse.mapping.get(edgeType)
          edgeTypeTarget <- ifNonEmpty(targets) {
            Option {
              EdgeTypeTarget(targets.map { t =>
                val filter = (e.name, e.index) match {
                  case (Some(n), _) => Some(EdgeNameFilter(n))
                  case (_, Some(i)) => Some(EdgeIndexFilter(i))
                  case _            => None
                }
                EdgeTypeTraverse(t, filter)
              })
            }
          }
        } yield {
          edgeTypeTarget
        }
      }
    }
  }
}