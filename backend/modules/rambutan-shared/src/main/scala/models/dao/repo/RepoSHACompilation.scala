package models

import silvousplay.imports._

sealed abstract class CompilationStatus(val identifier: String) extends Identifiable

object CompilationStatus extends Plenumeration[CompilationStatus] {
  case object Pending extends CompilationStatus("pending")
  case object InProgress extends CompilationStatus("in-progress")

  case object Failed extends CompilationStatus("failed")
  case object Complete extends CompilationStatus("complete")
}

case class RepoSHACompilation(
  shaId:  Int,
  status: CompilationStatus)
