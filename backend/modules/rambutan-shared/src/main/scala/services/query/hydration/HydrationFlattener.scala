package services

import models.query._

trait HydrationFlattener[From, TU] {
  def flatten(trace: List[From]): List[TU]
}

object HydrationFlattener {

  implicit def flattenNode[TU] = new HydrationFlattener[GraphTrace[TU], TU] {
    def flatten(trace: List[GraphTrace[TU]]): List[TU] = {
      trace.flatMap(_.allKeys)
    }
  }

  implicit def flattenNodeMap[TU] = new HydrationFlattener[Map[String, GraphTrace[TU]], TU] {
    def flatten(trace: List[Map[String, GraphTrace[TU]]]): List[TU] = {
      trace.flatMap(_.values.flatMap(_.allKeys).toList)
    }
  }
}
