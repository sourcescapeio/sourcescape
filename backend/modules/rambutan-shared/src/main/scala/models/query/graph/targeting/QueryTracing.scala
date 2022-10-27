package models.query

import play.api.libs.json._
import silvousplay.imports._

//
case class MapTracing[T, TU](inner: QueryTracing[T, TU], fromKey: String, toKey: String) extends QueryTracing[Map[String, T], TU] {

  /**
   * Unit level
   */
  def getId(unit: TU) = inner.getId(unit)

  def getKey(unit: TU) = inner.getKey(unit)

  def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
    inner.unitFromJs(js, edgeOverride)
  }

  /**
   *
   */
  private def getToTrace(trace: Map[String, T]) = trace.getOrElse(toKey, throw new Exception("invalid toKey"))

  private def getFromTrace(trace: Map[String, T]) = trace.getOrElse(fromKey, throw new Exception("invalid fromKey"))

  private def operateOnTo[R](trace: Map[String, T])(f: T => T) = {
    val toTrace = getToTrace(trace)

    trace ++ Map(
      toKey -> f(toTrace))
  }

  def getTerminus(trace: Map[String, T]): TU = {
    inner.getTerminus(getToTrace(trace))
  }

  def pushExternalKey(trace: Map[String, T]): Map[String, T] = {
    val fromTrace = getFromTrace(trace)

    trace ++ Map(
      toKey -> inner.pushExternalKey(fromTrace))
  }

  def replaceHeadNode(trace: Map[String, T], id: String, unit: TU): Map[String, T] = {
    // do nothing. this doesn't apply to generic graph
    operateOnTo(trace) { toTrace =>
      inner.replaceHeadNode(toTrace, id, unit)
    }
  }

  def traceHop(trace: Map[String, T], edgeType: GraphEdgeType, edgeJs: JsObject, initial: Boolean): Map[String, T] = {
    operateOnTo(trace) { toTrace =>
      inner.traceHop(toTrace, edgeType, edgeJs, initial)
    }
  }

  def pushCopy(trace: Map[String, T]): Map[String, T] = {
    operateOnTo(trace) { toTrace =>
      inner.pushCopy(toTrace)
    }
  }

  def dropHead(trace: Map[String, T]): Map[String, T] = {
    operateOnTo(trace) { toTrace =>
      inner.dropHead(toTrace)
    }
  }

  def injectNew(trace: Map[String, T], unit: TU): Map[String, T] = {
    operateOnTo(trace) { toTrace =>
      inner.injectNew(toTrace, unit)
    }
  }

  def newTrace(unit: TU): Map[String, T] = {
    Map(
      toKey -> inner.newTrace(unit))
  }

  def sortKey(trace: Map[String, T]): List[String] = {
    inner.sortKey(getToTrace(trace))
  }

  def ordering: Ordering[Map[String, T]] = {
    Ordering.by { a: Map[String, T] =>
      inner.sortKey(getToTrace(a)).mkString("|")
    }
  }

}

trait QueryTracingBasic[TU] {
  def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None): TU

  def getId(unit: TU): String

  def getKey(unit: TU): String
}

trait QueryTracingReduced[T, TU] extends QueryTracingBasic[TU] {
  def getTerminus(trace: T): TU
}

trait QueryTracing[T, TU] extends QueryTracingReduced[T, TU] {
  self =>

  def traceHop(unit: T, edgeType: GraphEdgeType, edgeJs: JsObject, initial: Boolean): T

  /**
   * Inherited
   */
  def pushExternalKey(trace: T): T

  def pushCopy(trace: T): T

  def dropHead(trace: T): T

  def injectNew(trace: T, unit: TU): T

  def newTrace(unit: TU): T

  def replaceHeadNode(trace: T, id: String, unit: TU): T

  /**
   * Sorting stuff
   */
  def sortKey(trace: T): List[String]

  final def joinKey(trace: T): List[String] = sortKey(trace) :+ headKey(trace)

  private def headKey(trace: T): String = getKey(getTerminus(trace))

  def ordering: Ordering[T]

}

object QueryTracing {
  case object GenericGraph extends QueryTracing[GraphTrace[GenericGraphUnit], GenericGraphUnit] {
    /**
     * Unit level
     */
    def getId(unit: GenericGraphUnit): String = {
      unit.id
    }

    def getKey(unit: GenericGraphUnit): String = {
      s"${unit.orgId}/${unit.id}"
    }

    /**
     * Trace level
     */

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

    def getTerminus(trace: GraphTrace[GenericGraphUnit]): GenericGraphUnit = {
      trace.terminusId
    }

    def pushCopy(trace: GraphTrace[GenericGraphUnit]): GraphTrace[GenericGraphUnit] = {
      trace.pushCopy
    }

    def dropHead(trace: GraphTrace[GenericGraphUnit]): GraphTrace[GenericGraphUnit] = {
      trace.dropHead
    }

    def replaceHeadNode(trace: GraphTrace[GenericGraphUnit], id: String, unit: GenericGraphUnit): GraphTrace[GenericGraphUnit] = {
      // do nothing. this doesn't apply to generic graph
      trace
    }

    def pushExternalKey(trace: GraphTrace[GenericGraphUnit]) = trace.copy(
      externalKeys = trace.externalKeys :+ getKey(trace.root),
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

    private def injectHead(trace: GraphTrace[GenericGraphUnit], unit: GenericGraphUnit) = {
      trace.injectHead(unit)
    }

    def sortKey(trace: GraphTrace[GenericGraphUnit]): List[String] = {
      trace.externalKeys :+ getKey(trace.root)
    }

    def ordering = {
      Ordering.by { a: GraphTrace[GenericGraphUnit] =>
        sortKey(a).mkString("|")
      }
    }
  }

  case object Basic extends QueryTracing[GraphTrace[TraceUnit], TraceUnit] {
    /**
     * Unit level
     */

    def getId(unit: TraceUnit): String = {
      unit.id
    }

    def getKey(unit: TraceUnit): String = {
      s"${unit.key}/${unit.path}/${unit.id}"
    }

    def unitFromJs(js: JsObject, edgeOverride: Option[GraphEdgeType] = None) = {
      val key = (js \ "_source" \ "key").as[String]
      val path = (js \ "_source" \ "path").as[String]
      val id = (js \ "_source" \ "id").as[String]
      val name = (js \ "_source" \ "name").asOpt[String]
      val index = (js \ "_source" \ "index").asOpt[Int]

      TraceUnit(edgeOverride, key, path, id, name, index)
    }

    /**
     * Trace level
     */
    def getTerminus(trace: GraphTrace[TraceUnit]): TraceUnit = {
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

    private def injectHead(trace: GraphTrace[TraceUnit], unit: TraceUnit) = {
      trace.injectHead(unit)
    }

    def newTrace(unit: TraceUnit) = {
      GraphTrace(Nil, Nil, SubTrace(Nil, unit))
    }

    def replaceHeadNode(trace: GraphTrace[TraceUnit], id: String, unit: TraceUnit): GraphTrace[TraceUnit] = {
      // do nothing. this doesn't apply to generic graph
      trace.copy(
        terminus = trace.terminus.copy(
          terminus = unit))
    }

    def pushExternalKey(trace: GraphTrace[TraceUnit]) = trace.copy(
      externalKeys = trace.externalKeys :+ getKey(trace.root),
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
      trace.externalKeys :+ getKey(trace.root)
    }

    def ordering = {
      Ordering.by { a: GraphTrace[TraceUnit] =>
        sortKey(a).mkString("|")
      }
    }
  }
}