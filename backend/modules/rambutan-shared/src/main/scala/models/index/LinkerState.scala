package models

import silvousplay.imports._
import play.api.libs.json._

case class RequireLinkerState(file: String, requirePath: List[String], id: String)

// exportMap is path to id
case class LinkerState(exports: Map[String, String], requires: List[RequireLinkerState]) {
  def add(next: LinkerState) = {
    LinkerState(exports ++ next.exports, requires ++ next.requires)
  }
}
object LinkerState {
  def empty = LinkerState(Map.empty[String, String], List.empty[RequireLinkerState])
}
