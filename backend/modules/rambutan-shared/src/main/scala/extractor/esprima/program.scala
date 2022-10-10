package models.extractor

import models.{ CodeLocation, CodeRange, ExtractorContext }
import models.index.{ GraphResult, StandardEdgeBuilder }
import models.index.esprima._
import silvousplay.imports._
import akka.stream.scaladsl.SourceQueue
import play.api.libs.json._
import play.api.libs.functional.syntax._
import java.nio.file.Paths
import silvousplay.api.SpanContext

package object esprima {

  type ESPrimaEdgeBuilder = StandardEdgeBuilder[ESPrimaEdgeType, ESPrimaNodeType]

  protected[esprima] case class StatementWrapper(
    codeRange:   CodeRange,
    expressions: List[ExpressionWrapper[ESPrimaNodeBuilder]],
    children:    List[StatementWrapper]) {

    // def allNodesLimitingFunction: List[ESPrimaNodeBuilder] = {
    //   expressions.flatMap(_.allNodesLimitingFunction) ++ children.flatMap(_.allNodesLimitingFunction)
    // }

    def allExpressions: List[ExpressionWrapper[ESPrimaNodeBuilder]] = {
      expressions ++ children.flatMap(_.allExpressions)
    }

    def allNodes: List[ESPrimaNodeBuilder] = {
      expressions.flatMap(_.allNodes) ++ children.flatMap(_.allNodes)
    }

    def allEdges: List[ESPrimaEdgeBuilder] = {
      expressions.flatMap(_.allEdges) ++ children.flatMap(_.allEdges)
    }
  }

  protected[esprima] object StatementWrapper {
    def empty(range: CodeRange) = StatementWrapper(range, Nil, Nil)
  }

  protected[esprima] case class ExpressionWrapper[+T <: ESPrimaNodeBuilder](
    node:            T,
    codeRange:       CodeRange,
    children:        List[ExpressionWrapper[ESPrimaNodeBuilder]],
    additionalNodes: List[ESPrimaNodeBuilder],
    edges:           List[ESPrimaEdgeBuilder]) {

    // special case for function extraction
    // def allNodesLimitingFunction: List[ESPrimaNodeBuilder] = {
    //   node match {
    //     case n: FunctionNode => n :: Nil
    //     case _ => node :: (additionalNodes ++ children.flatMap(_.allNodesLimitingFunction))
    //   }
    // }

    def allNodes: List[ESPrimaNodeBuilder] = {
      node :: (additionalNodes ++ children.flatMap(_.allNodes))
    }

    def allEdges: List[ESPrimaEdgeBuilder] = {
      edges ++ children.flatMap(_.allEdges)
    }

    def allIdentifiers = allNodes.flatMap {
      case i @ IdentifierReferenceNode(_, _, _) => Some(i.toIdentifier)
      case i @ IdentifierNode(_, _, _)          => Some(i)
      case _                                    => None
    }
  }

  protected[esprima] object ExpressionWrapper {
    def single[T <: ESPrimaNodeBuilder](node: T) = ExpressionWrapper[T](
      node,
      node.range,
      Nil,
      Nil,
      Nil)
  }

  sealed case class ESPrimaContext(
    path: String,
    // identifiers
    identifierStack:     List[List[IdentifierNode]],
    declaredIdentifiers: List[IdentifierNode], // these are never popped. last across a program / blockstatement
    identifierLookup:    Map[String, List[IdentifierNode]],
    // labels
    labelStack:  List[String],
    labelLookup: Map[String, LabelNode],
    context:     SpanContext,
    // exports
    currentExport: Option[ExportNode],
    // paths
    debugPath: List[String]) extends ExtractorContext {

    def pushDebug(addPath: String): ESPrimaContext = {
      this.copy(
        debugPath = addPath :: this.debugPath)
    }

    def replaceDebug(newPath: List[String]): ESPrimaContext = {
      this.copy(
        debugPath = newPath)
    }

    // TODO: replace
    val InPackage = List(
      "packages/shared/src",
      "packages/worker/src",
      "packages/chrome/src",
      "packages/client/src",
      "packages/electron/src",
      "packages/server/src")

    val RemapPackage = Map(
      "slapdash-shared/dist" -> "packages/shared/src",
      "slapdash-server/dist" -> "packages/server/src",
      "slapdash-chrome/dist" -> "packages/chrome/src",
      "slapdash-client/dist" -> "packages/client/src")

    def requireNode(range: CodeRange, name: String) = {
      val searches = if (name.startsWith("./") || name.startsWith("../")) {
        // Relative path
        val pathWithoutHead = path.split("/").dropRight(1).mkString("/")
        val base = Paths.get(pathWithoutHead).resolve(name).normalize().toString()
        base :: Nil
      } else {
        val remap = RemapPackage.find(r => name.startsWith(r._1))
        val inPackage = InPackage.find(path.startsWith)
        (remap, inPackage) match {
          case (Some((remapFrom, remapTo)), _) => {
            name.replaceFirst(remapFrom, remapTo) :: Nil
          }
          case (None, Some(inp)) => {
            s"${inp}/${name}" :: Nil
          }
          case _ => {
            Nil
          }
        }
      }
      RequireNode(Hashing.uuid, range, name, searches)
    }

    // replace /index.ts
    // replace .ts

    // TODO: shitty ass hardcode
    private def additionalPaths(path: String) = {
      path match {
        case p if p.endsWith("/index.ts") || p.endsWith("/index.js") => {
          p.dropRight("/index.ts".length) :: p.dropRight(".ts".length) :: Nil
        }
        case p if p.endsWith("/index.tsx") || p.endsWith("/index.jsx") => {
          p.dropRight("/index.tsx".length) :: p.dropRight(".tsx".length) :: Nil
        }
        case p if p.endsWith(".ts") || p.endsWith(".js") => {
          p.dropRight(".ts".length) :: Nil
        }
        case p if p.endsWith(".tsx") || p.endsWith(".jsx") => {
          p.dropRight(".tsx".length) :: Nil
        }
        case _ => Nil
      }
    }

    def getExport(range: CodeRange) = currentExport.getOrElse {
      // TODO: code range linked to key. frowntown
      // i.e. if I have export enum A, export enum B, the export node will be on B's code range
      ExportNode(Hashing.uuid, range, path, additionalPaths(path))
    }

    def pushExport(node: ExportNode) = {
      if (currentExport.isDefined) {
        this
      } else {
        replaceExport(node)
      }
    }

    def replaceExport(node: ExportNode) = {
      this.copy(currentExport = Some(node))
    }

    def pushLabel(name: String, node: LabelNode) = {
      this.copy(
        labelStack = name :: labelStack,
        labelLookup = labelLookup + (name -> node))
    }

    def popLabel = {
      val (name, remainder) = labelStack match {
        case n :: r => (n, r)
        case Nil    => throw new Exception("invalid pop")
      }
      this.copy(
        labelStack = remainder,
        labelLookup = labelLookup - name)
    }

    def pushIdentifiers(add: List[IdentifierNode]) = {
      val addMap = add.map { a =>
        a.name -> a
      }.toMap
      // add to lookup
      val newIdentifierLookup = addMap.map {
        case (k, v) => k -> List(v)
      } ++ identifierLookup.map {
        case (k, v) => addMap.get(k) match {
          case Some(vv) => (k -> (vv :: v))
          case _        => (k -> v)
        }
      }
      this.copy(
        identifierStack = add :: identifierStack,
        identifierLookup = newIdentifierLookup)
    }

    def popIdentifiers = {
      val (popped, remainder) = identifierStack match {
        case h :: r => (h, r)
        case Nil    => throw new Exception("invalid pop")
      }

      // remove popped from lookup
      val poppedMap = popped.map { p =>
        p.name -> p
      }.toMap
      val newIdentifierLookup = identifierLookup.map {
        case (k, v) => poppedMap.get(k) match {
          case Some(vv) => k -> v.filter(_.id =/= vv.id)
          case _        => k -> v
        }
      }

      this.copy(
        identifierStack = remainder,
        identifierLookup = newIdentifierLookup)
    }

    // permanent injection into context
    def declareIdentifier(add: IdentifierNode) = declareIdentifiers(List(add))
    def declareIdentifiers(add: List[IdentifierNode]) = {
      this.copy(
        declaredIdentifiers = add ++ declaredIdentifiers)
    }

    // extraction
    def findIdentifier(name: String): Option[IdentifierNode] = {
      declaredIdentifiers.find(_.name =?= name) orElse {
        identifierLookup.get(name).flatMap(_.headOption)
      }
    }

    def findLabel(labelName: String): Option[LabelNode] = {
      labelLookup.get(labelName)
    }
  }

  object ESPrimaContext {
    def empty(path: String, context: SpanContext) = ESPrimaContext(
      path,
      Nil,
      Nil,
      Map(),
      Nil,
      Map(),
      context,
      None,
      Nil)
  }

  implicit val lang = Language[ESPrimaContext](
    typeKey = "type",
    (
      (JsPath \ "loc" \ "start").read[CodeLocation] and
      (JsPath \ "loc" \ "end").read[CodeLocation] and
      (JsPath \ "range").read[Array[Int]])((a, b, c) => CodeRange(a, b, c(0), c(1))))

  def Program = {
    node("Program") ~
      tup("body" -> sequence(Statements.StatementListItem))
  } mapExtraction {
    case (context, codeRange, body) => {
      val nodes = body.flatMap(_.allNodes)
      val edges = body.flatMap(_.allEdges)
      GraphResult(context.path, nodes, edges)
    }
  }
}
