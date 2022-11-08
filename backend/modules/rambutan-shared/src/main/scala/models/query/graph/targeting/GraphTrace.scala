package models.query

import play.api.libs.json._

case class SubTrace[T](tracesInternal: Vector[T], terminus: T) {
  def allKeys = tracesInternal :+ terminus

  def inject(id: T) = SubTrace(allKeys, id)

  def root: T = tracesInternal.headOption match {
    case Some(r) => r
    case _       => terminus
  }

  //
  def mapTrace[V](f: T => V) = {
    SubTrace(tracesInternal.map(f), f(terminus))
  }

  def wipe = this.copy(tracesInternal = Vector())

  def dto(implicit writes: Writes[T]) = {
    Json.obj(
      "trace" -> tracesInternal.map(i => Json.toJson(i)),
      "node" -> Json.toJson(terminus))
  }
}

case class GraphTrace[T](externalKeys: Vector[String], tracesInternal: Vector[SubTrace[T]], terminus: SubTrace[T]) {

  def allKeys = tracesInternal.flatMap(_.allKeys) ++ terminus.allKeys

  def terminusId = terminus.terminus

  def root: T = tracesInternal.headOption match {
    case Some(r) => r.root
    case _       => terminus.root
  }

  def pushCopy = {
    GraphTrace(
      externalKeys,
      tracesInternal :+ terminus,
      SubTrace(Vector(), terminusId))
  }

  def injectNew(id: T) = {
    // pop and create new
    GraphTrace(
      externalKeys,
      tracesInternal :+ terminus,
      SubTrace(Vector(), id))
  }

  def injectHead(id: T) = {
    this.copy(terminus = terminus.inject(id))
  }

  // def dropHead = {
  //   tracesInternal match {
  //     case head :: rest => GraphTrace(externalKeys, rest, head)
  //     case _            => throw new Exception("could not drop head")
  //   }
  // }

  //
  def mapTrace[V](f: T => V) = {
    GraphTrace(
      externalKeys,
      tracesInternal.map(_.mapTrace(f)),
      terminus.mapTrace(f))
  }

  def dto(implicit writes: Writes[T]) = {
    tracesInternal.zipWithIndex.map {
      case (trace, idx) => s"trace_${idx}" -> trace.dto
    }.toMap ++ Map(
      "terminus" -> terminus.dto,
      "externalKeys" -> Json.toJson(externalKeys))
  }

  def json(implicit writes: Writes[T]) = Json.toJson(dto)
}
