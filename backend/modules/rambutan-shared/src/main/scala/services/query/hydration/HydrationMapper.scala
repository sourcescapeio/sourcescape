package services

import models.Errors
import models.query._
import models.graph._
import models.index.GraphNode
import play.api.libs.json._

trait HydrationMapper[K, V, From, To] {

  // def allTraces(trace: From): List[TU]

  def hydrate(trace: From, nodeMap: Map[K, V]): To
}

object HydrationMapper {
  /**
   * Node
   */
  implicit val flatTraceUnitHydration = new HydrationMapper[TraceKey, JsObject, GraphTrace[TraceUnit], GraphTrace[(String, GraphNode)]] {
    override def hydrate(trace: GraphTrace[TraceUnit], nodeMap: Map[TraceKey, JsObject]): GraphTrace[(String, GraphNode)] = {
      val traceExtractor = implicitly[HasTraceKey[TraceUnit]]
      trace.mapTrace { i =>
        val edgeTypeIdentifer = traceExtractor.edgeTypeIdentifier(i)

        if (!nodeMap.contains(traceExtractor.traceKey(i))) {
          println(traceExtractor.traceKey(i))
        }

        edgeTypeIdentifer -> nodeMap.getOrElse(
          traceExtractor.traceKey(i),
          throw Errors.streamError("broken jsobject during hydration")).as[GraphNode]
      }
    }
  }

  implicit val mapTraceUnitHydration = new HydrationMapper[TraceKey, JsObject, Map[String, GraphTrace[TraceUnit]], Map[String, GraphTrace[(String, GraphNode)]]] {
    override def hydrate(trace: Map[String, GraphTrace[TraceUnit]], nodeMap: Map[TraceKey, JsObject]): Map[String, GraphTrace[(String, GraphNode)]] = {
      trace.view.mapValues { tt =>
        flatTraceUnitHydration.hydrate(tt, nodeMap)
      }.toMap
    }
  }

  implicit val flatGenericUnitHydration = new HydrationMapper[TraceKey, JsObject, GraphTrace[GenericGraphUnit], GraphTrace[GenericGraphNode]] {
    override def hydrate(trace: GraphTrace[GenericGraphUnit], nodeMap: Map[TraceKey, JsObject]): GraphTrace[GenericGraphNode] = {
      val traceExtractor = implicitly[HasTraceKey[GenericGraphUnit]]
      trace.mapTrace { i =>
        nodeMap.getOrElse(
          traceExtractor.traceKey(i),
          throw Errors.streamError("broken jsobject during hydration")).as[GenericGraphNode]
      }
    }
  }

  implicit val mapGenericUnitHydration = new HydrationMapper[TraceKey, JsObject, Map[String, GraphTrace[GenericGraphUnit]], Map[String, GraphTrace[GenericGraphNode]]] {
    override def hydrate(trace: Map[String, GraphTrace[GenericGraphUnit]], nodeMap: Map[TraceKey, JsObject]): Map[String, GraphTrace[GenericGraphNode]] = {
      trace.view.mapValues { tt =>
        flatGenericUnitHydration.hydrate(tt, nodeMap)
      }.toMap
    }
  }

  /**
   * Code
   */
  implicit val flatTraceCodeHydration = new HydrationMapper[FileKey, (String, Array[String]), GraphTrace[(String, GraphNode)], GraphTrace[QueryNode]] {
    override def hydrate(trace: GraphTrace[(String, GraphNode)], codeMap: Map[FileKey, (String, Array[String])]): GraphTrace[QueryNode] = {
      trace.mapTrace {
        case (edgeType, item) => {
          val key = item.fileKey
          val (text, splitText) = codeMap.getOrElse(key, throw Errors.streamError("file not found"))
          QueryNode.extract(item, edgeType, text, splitText)
        }
      }
    }
  }

  implicit val mapTraceCodeHydration = new HydrationMapper[FileKey, (String, Array[String]), Map[String, GraphTrace[(String, GraphNode)]], Map[String, GraphTrace[QueryNode]]] {
    override def hydrate(trace: Map[String, GraphTrace[(String, GraphNode)]], codeMap: Map[FileKey, (String, Array[String])]): Map[String, GraphTrace[QueryNode]] = {
      trace.view.mapValues { tt =>
        flatTraceCodeHydration.hydrate(tt, codeMap)
      }.toMap
    }
  }

  // No op << deprecate this eventually
  implicit val flatGenericCodeHydration = new HydrationMapper[FileKey, (String, Array[String]), GraphTrace[GenericGraphNode], GraphTrace[GenericGraphNode]] {
    override def hydrate(trace: GraphTrace[GenericGraphNode], codeMap: Map[FileKey, (String, Array[String])]): GraphTrace[GenericGraphNode] = {
      trace
    }
  }
}
