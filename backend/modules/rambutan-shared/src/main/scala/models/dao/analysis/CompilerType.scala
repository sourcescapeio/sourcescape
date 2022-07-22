package models

import silvousplay.imports._
import scala.meta._

sealed abstract class CompilerType(
  val identifier:   String,
  val analysisType: AnalysisType,
  val extensions:   List[String],
  val serverConfig: String) extends Identifiable {
  def isValid(file: String): Boolean
}

object CompilerType extends Plenumeration[CompilerType] {

  // scala metal-faced terrorist
  case object MadVillain extends CompilerType(
    identifier = "madvillain",
    analysisType = AnalysisType.ScalaSemanticDB,
    extensions = "sbt" :: Nil,
    serverConfig = "madvillain.server") {
    def isValid(file: String): Boolean = {
      file =?= "build.sbt"
    }
  }
}
