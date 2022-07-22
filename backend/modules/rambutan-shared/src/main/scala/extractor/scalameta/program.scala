package models.extractor

import models.{ CodeRange, CodeLocation }
import models.index.scalameta._
import models.index.{ GraphResult, StandardEdgeBuilder }
import scala.meta._
import akka.stream.scaladsl.SourceQueue

import scala.meta.internal.semanticdb.TextDocuments

package object scalameta {

  type ScalaMetaEdgeBuilder = StandardEdgeBuilder[ScalaMetaEdgeType, ScalaMetaNodeType]

  case class ScalaMetaContext(
    packageName: String,
    analysis:    TextDocuments,
    logQueue:    SourceQueue[(CodeRange, String)]) {

    def log(range: CodeRange, message: String) = logQueue.offer((range, message))

    def newPackage(name: String) = this.copy(packageName = name)

    def appendPackage(name: String) = this.copy(packageName = s"${packageName}.${name}")
  }

  object ScalaMetaContext {
    def empty(analysis: TextDocuments, logQueue: SourceQueue[(CodeRange, String)]) = {
      ScalaMetaContext("", analysis, logQueue)
    }
  }

  // helpers
  protected[scalameta] def codeRange(pos: Position): CodeRange = {
    CodeRange(
      CodeLocation(pos.startLine, pos.startColumn),
      CodeLocation(pos.endLine, pos.endColumn),
      pos.start,
      pos.end)
  }

  protected def prettyPrint(o: Any): String = {
    pprint.apply {
      o
    }.plainText
  }

  protected[scalameta] case class ExpressionWrapper[+T <: ScalaMetaNodeBuilder](
    node:            T,
    codeRange:       CodeRange,
    children:        List[ExpressionWrapper[ScalaMetaNodeBuilder]],
    additionalNodes: List[ScalaMetaNodeBuilder],
    edges:           List[ScalaMetaEdgeBuilder]) {

    def allNodes: List[ScalaMetaNodeBuilder] = {
      node :: (additionalNodes ++ children.flatMap(_.allNodes))
    }

    def allEdges: List[ScalaMetaEdgeBuilder] = {
      edges ++ children.flatMap(_.allEdges)
    }
  }

  object Source {
    def extract(path: String, in: Source)(implicit context: ScalaMetaContext) = {
      val bases = in.stats.flatMap(s => extractTree(s)(context))
      GraphResult(
        path,
        bases.flatMap(_.allNodes),
        bases.flatMap(_.allEdges))
    }

    def extractTree(in: Tree)(context: ScalaMetaContext): List[ExpressionWrapper[ScalaMetaNodeBuilder]] = {
      in match {
        case p: Pkg => {
          // https://www.javadoc.io/doc/org.scalameta/trees_2.12/latest/scala/meta/Pkg.html
          val newContext = context.newPackage(p.name.value)
          p.stats.flatMap(c => extractTree(c)(newContext))
        }
        case po: Pkg.Object => {
          // https://www.javadoc.io/doc/org.scalameta/trees_2.12/latest/scala/meta/Pkg$$Object.html
          val newContext = context.appendPackage(po.name.value)
          // mods
          defn.extractTemplate(po.templ)(newContext)
          Nil
        }
        //
        case i: Import => {
          i.importers.foreach {
            case ii @ Importer(ref, importees) => {
              println(s"Ignored import: ${ii.tokens.mkString}")
            }
          }
          Nil
        }
        case o: Defn.Object => {
          defn.Object.extract(o)(context) :: Nil
        }
        case t: Defn.Trait => {
          defn.Trait.extract(t)(context) :: Nil
        }
        case o => {
          val unknownStr = s"Unknown structure: ${o.getClass().getTypeName()}\n\nStructure:\n${prettyPrint(o)}\n\nOriginal:\n${o.tokens.mkString}"
          context.log(codeRange(o.pos), unknownStr)
          Nil
        }
      }
    }
  }
}

