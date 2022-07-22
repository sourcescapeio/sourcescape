package models.query

import play.api.libs.json._

// NOTE: all traces in reverse order for efficiency
case class SubTrace[T](tracesInternal: List[T], terminus: T) {
  def inject(id: T) = SubTrace(terminus :: tracesInternal, id)

  def allKeys = terminus :: tracesInternal

  // NOTE: may have been ill advised to reverse
  def root: T = tracesInternal.lastOption match {
    case Some(r) => r
    case _       => terminus
  }

  //
  def mapTrace[V](f: T => V) = {
    SubTrace(tracesInternal.map(f), f(terminus))
  }

  def wipe = this.copy(tracesInternal = Nil)

  def dto(implicit writes: Writes[T]) = {
    Json.obj(
      "trace" -> tracesInternal.reverse.map(i => Json.toJson(i)),
      "node" -> Json.toJson(terminus))
  }
}

case class GraphTrace[T](externalKeys: List[String], tracesInternal: List[SubTrace[T]], terminus: SubTrace[T]) {

  // left
  def joinKey(implicit targeting: HasBasicExtraction[T]): List[String] = sortKey :+ headKey
  // right
  def sortKey(implicit targeting: HasBasicExtraction[T]): List[String] = externalKeys :+ targeting.getKey(root)

  def headKey(implicit targeting: HasBasicExtraction[T]): String = targeting.getKey(terminusId)

  def allKeys = tracesInternal.flatMap(_.allKeys) ++ terminus.allKeys

  def pushExternalKey(implicit targeting: HasBasicExtraction[T]) = this.copy(
    externalKeys = externalKeys :+ targeting.getKey(root),
    tracesInternal = Nil,
    terminus = terminus.wipe)

  def terminusId = terminus.terminus

  // NOTE: may have been ill advised to reverse
  def root: T = tracesInternal.lastOption match {
    case Some(r) => r.root
    case _       => terminus.root
  }

  def pushCopy = {
    GraphTrace(
      externalKeys,
      terminus :: tracesInternal,
      SubTrace(Nil, terminusId))
  }

  def injectNew(id: T) = {
    // pop and create new
    GraphTrace(
      externalKeys,
      terminus :: tracesInternal,
      SubTrace(Nil, id))
  }

  def injectHead(id: T) = {
    this.copy(terminus = terminus.inject(id))
  }

  def dropHead = {
    tracesInternal match {
      case head :: rest => GraphTrace(externalKeys, rest, head)
      case _            => throw new Exception("could not drop head")
    }
  }

  //
  def mapTrace[V](f: T => V) = {
    GraphTrace(
      externalKeys,
      tracesInternal.map(_.mapTrace(f)),
      terminus.mapTrace(f))
  }

  def dto(implicit writes: Writes[T]) = {
    tracesInternal.reverse.zipWithIndex.map {
      case (trace, idx) => s"trace_${idx}" -> trace.dto
    }.toMap ++ Map(
      "terminus" -> terminus.dto,
      "externalKeys" -> Json.toJson(externalKeys))
  }

  def json(implicit writes: Writes[T]) = Json.toJson(dto)
}
