package models

import silvousplay.imports._
import scala.meta._

sealed trait AnalysisType extends Identifiable {
  val identifier: String
  val extensions: List[String]
  // val shouldWrite: Boolean

  def path(base: String, file: String) = {
    s"${base}/${identifier}/${file}"
  }

  def isValidBlob(path: String) = {
    extensions.exists(e => path.endsWith(s".${e}"))
  }
}

sealed abstract class ServerAnalysisType(
  val identifier: String,
  val extensions: List[String],
  val server:     String) extends AnalysisType {
  // val shouldWrite = true
}

sealed abstract class NoOpAnalysisType(
  val identifier: String,
  val extensions: List[String]) extends AnalysisType {

  // val shouldWrite = false

  // def validate(content: String) = {
  //   content.parse[Source].get
  // }
}

sealed abstract class CompiledAnalysisType(
  val identifier:        String,
  val extensions:        List[String],
  val analysisExtension: String) extends AnalysisType {

  // val shouldWrite = false

  def pathWithExtension(path: String) = s"${path}.${analysisExtension}"
}

object AnalysisType extends Plenumeration[AnalysisType] {
  /**
   * JS/TS
   */
  case object ESPrimaJavascript extends ServerAnalysisType(
    "esprima_js",
    List("js", "jsx"),
    server = "primadonna.server")

  case object ESPrimaTypescript extends ServerAnalysisType(
    "esprima_ts",
    List("ts", "tsx"),
    server = "primadonna.server")

  /**
   *  Ruby
   */
  case object RubyParser extends ServerAnalysisType(
    "ruby",
    List("rb"),
    server = "dorothy.server")

  /**
   * Scala
   */
  case object ScalaSemanticDB extends CompiledAnalysisType(
    "scala-semanticdb",
    List("scala"),
    "semanticdb")
}
