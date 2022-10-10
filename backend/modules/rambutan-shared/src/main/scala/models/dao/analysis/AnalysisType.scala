package models

import silvousplay.imports._
import scala.meta._

sealed abstract class AnalysisType(
  val identifier: String,
  val extensions: List[String],
  val server:     String) extends Identifiable {

  // val shouldWrite = true

  def path(base: String, file: String) = {
    s"${base}/${identifier}/${file}"
  }

  def isValidBlob(path: String) = {
    extensions.exists(e => path.endsWith(s".${e}"))
  }
}

object AnalysisType extends Plenumeration[AnalysisType] {
  /**
   * JS/TS
   */
  case object ESPrimaJavascript extends AnalysisType(
    "esprima_js",
    List("js", "jsx"),
    server = "primadonna.server")

  case object ESPrimaTypescript extends AnalysisType(
    "esprima_ts",
    List("ts", "tsx"),
    server = "primadonna.server")

  /**
   *  Ruby
   */
  case object RubyParser extends AnalysisType(
    "ruby",
    List("rb"),
    server = "dorothy.server")
}
