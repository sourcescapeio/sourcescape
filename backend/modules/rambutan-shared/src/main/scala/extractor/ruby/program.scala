package models.extractor

import models.{ CodeLocation, CodeRange, ExtractorContext }
import models.index.{ GraphResult, StandardEdgeBuilder }
import models.index.ruby._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import akka.stream.scaladsl.SourceQueue
import silvousplay.imports._
import silvousplay.api.SpanContext

package object ruby {

  type RubyEdgeBuilder = StandardEdgeBuilder[RubyEdgeType, RubyNodeType]

  protected[ruby] case class StatementWrapper(
    codeRange:   CodeRange,
    expressions: List[ExpressionWrapper[RubyNodeBuilder]],
    children:    List[StatementWrapper]) {

    // def allNodesLimitingFunction: List[ESPrimaNodeBuilder] = {
    //   expressions.flatMap(_.allNodesLimitingFunction) ++ children.flatMap(_.allNodesLimitingFunction)
    // }

    def allExpressions: List[ExpressionWrapper[RubyNodeBuilder]] = {
      expressions ++ children.flatMap(_.allExpressions)
    }

    def allNodes: List[RubyNodeBuilder] = {
      expressions.flatMap(_.allNodes) ++ children.flatMap(_.allNodes)
    }

    def allEdges: List[RubyEdgeBuilder] = {
      expressions.flatMap(_.allEdges) ++ children.flatMap(_.allEdges)
    }
  }

  protected[ruby] object StatementWrapper {
    def empty(range: CodeRange) = StatementWrapper(range, Nil, Nil)
  }

  protected[ruby] case class ExpressionWrapper[+T <: RubyNodeBuilder](
    node:            T,
    codeRange:       CodeRange,
    children:        List[ExpressionWrapper[RubyNodeBuilder]],
    additionalNodes: List[RubyNodeBuilder],
    edges:           List[RubyEdgeBuilder]) {

    def allNodes: List[RubyNodeBuilder] = {
      node :: (additionalNodes ++ children.flatMap(_.allNodes))
    }

    def allEdges: List[RubyEdgeBuilder] = {
      edges ++ children.flatMap(_.allEdges)
    }

    def allIdentifiers = allNodes.flatMap {
      // case i @ IdentifierReferenceNode(_, _, _) => Some(i.toIdentifier)
      // case i @ IdentifierNode(_, _, _)          => Some(i)
      case _ => None
    }
  }

  protected[ruby] object ExpressionWrapper {
    def single[T <: RubyNodeBuilder](node: T) = ExpressionWrapper[T](
      node,
      node.range,
      Nil,
      Nil,
      Nil)
  }

  case class RubyContext(
    path:    String,
    context: SpanContext,
    // currentMethod
    currentMethod: Option[MethodNodeBuilder],
    //
    localVars:    Map[String, LVarNode],
    instanceVars: Map[String, IVarNode],
    debugPath:    List[String]) extends ExtractorContext {

    def pushDebug(addPath: String): RubyContext = {
      this.copy(
        debugPath = addPath :: this.debugPath)
    }

    def replaceDebug(newPath: List[String]): RubyContext = {
      this.copy(
        debugPath = newPath)
    }

    // method
    def pushCurrentMethod(node: MethodNodeBuilder) = {
      this.copy(currentMethod = Some(node))
    }

    // variables
    def pushInstanceVar(name: String, node: IVarNode) = {
      this.copy(
        instanceVars = instanceVars + (name -> node))
    }

    def getInstanceVar(name: String) = {
      instanceVars.get(name)
    }

    def pushLocalVar(name: String, node: LVarNode) = {
      this.copy(
        localVars = localVars + (name -> node))
    }

    def getLocalVar(name: String) = {
      localVars.get(name)
    }
  }

  object RubyContext {
    def empty(path: String, context: SpanContext) = {
      RubyContext(
        path,
        context,
        currentMethod = None,
        localVars = Map.empty[String, LVarNode],
        instanceVars = Map.empty[String, IVarNode],
        debugPath = Nil)
    }
  }

  implicit val lang = Language[RubyContext](
    typeKey = "type",
    (
      (JsPath \ "location" \ "start_line").readNullable[Int] and
      (JsPath \ "location" \ "start_column").readNullable[Int] and
      (JsPath \ "location" \ "end_line").readNullable[Int] and
      (JsPath \ "location" \ "end_column").readNullable[Int] and
      (JsPath \ "location" \ "start_index").readNullable[Int] and
      (JsPath \ "location" \ "end_index").readNullable[Int])((a, b, c, d, e, f) => CodeRange(
        CodeLocation(a.getOrElse(0), b.getOrElse(0)),
        CodeLocation(c.getOrElse(0), d.getOrElse(0)),
        e.getOrElse(0),
        f.getOrElse(0))))

  def Start = Statements.Statement mapExtraction {
    case (context, codeRange, body) => {
      val nodes = body.allNodes
      val edges = body.allEdges
      GraphResult(context.path, nodes, edges)
    }
  }
}
