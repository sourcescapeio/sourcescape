package models

import models.index.GraphResult
import silvousplay.imports._
import play.api.libs.json._
import java.nio.file.Paths
import akka.stream.scaladsl.SourceQueue
import akka.util.ByteString
import scala.meta._
import pprint._
import scala.meta.internal.semanticdb.TextDocuments
import models.query._

sealed abstract class IndexType(
  val identifier:    String,
  val analysisTypes: List[AnalysisType],
  val nodePredicate: Plenumeration[_ <: NodePredicate],
  val edgePredicate: Plenumeration[_ <: EdgePredicate]) extends Identifiable {

  def indexer(path: String, content: ByteString, analysis: ByteString, logQueue: SourceQueue[(CodeRange, String)]): GraphResult

  def isValidBlob(path: String) = analysisTypes.exists(_.isValidBlob(path))

  def prettyPrint(content: ByteString, analysis: ByteString): String

  val nodeIndexName = s"${identifier}_node"
  val edgeIndexName = s"${identifier}_edge"
}

object IndexType extends Plenumeration[IndexType] {
  case object Javascript extends IndexType(
    "javascript",
    AnalysisType.ESPrimaJavascript :: AnalysisType.ESPrimaTypescript :: Nil,
    JavascriptNodePredicate,
    JavascriptEdgePredicate) {

    def indexer(path: String, content: ByteString, analysis: ByteString, logQueue: SourceQueue[(CodeRange, String)]) = {
      val js = Json.parse(analysis.utf8String)
      val emptyAcc = extractor.esprima.ESPrimaContext.empty(path, logQueue)
      extractor.esprima.Program.extract(
        emptyAcc,
        js) match {
        case Right((_, a)) => a
        case Left(err)     => err.toException
      }
    }

    def prettyPrint(content: ByteString, analysis: ByteString) = {
      Json.prettyPrint(Json.parse(analysis.utf8String))
    }
  }

  case object Ruby extends IndexType(
    "ruby",
    AnalysisType.RubyParser :: Nil,
    RubyNodePredicate,
    RubyEdgePredicate) {
    def indexer(path: String, content: ByteString, analysis: ByteString, logQueue: SourceQueue[(CodeRange, String)]) = {
      val js = Json.parse(analysis.utf8String)
      val emptyAcc = extractor.ruby.RubyContext.empty(path, logQueue)
      // emptyAcc
      extractor.ruby.Start.extract(
        emptyAcc,
        js) match {
        case Right((_, a)) => a
        case Left(err)     => err.toException
      }
    }

    def prettyPrint(content: ByteString, analysis: ByteString) = {
      Json.prettyPrint(Json.parse(analysis.utf8String))
    }
  }
}
