package models.index

import silvousplay.imports._

trait EdgeType extends Identifiable {
  val isContains: Boolean = false
}

object EdgeType {
  case object Link extends EdgeType {
    val identifier = "link"
  }
}