package models

import play.api.libs.json._

object ESQuery {
  val matchAll = Json.obj("match_all" -> Json.obj())

  def wildcardSearch(key: String, q: String) = Json.obj(
    "wildcard" -> Json.obj(
      key -> q))

  def termSearch(key: String, q: String) = Json.obj(
    "term" -> Json.obj(
      key -> q))

  def termsSearch(key: String, q: List[String]) = Json.obj(
    "terms" -> Json.obj(
      key -> q))

  def prefixSearch(key: String, q: String) = Json.obj(
    "prefix" -> Json.obj(
      key -> Json.obj(
        "value" -> q)))

  def regexpSearch(key: String, q: String) = Json.obj(
    "regexp" -> Json.obj(
      key -> Json.obj(
        "value" -> q)))

  def rangeSearch(key: String, gte: Option[Int] = None, lte: Option[Int] = None) = Json.obj(
    "range" -> Json.obj(
      key -> Json.obj(
        "gte" -> gte,
        "lte" -> lte)))

  def bool(filter: List[JsObject] = Nil, must: List[JsObject] = Nil, should: List[JsObject] = Nil, mustNot: List[JsObject] = Nil) = {
    val mustObj = must match {
      case Nil       => Json.obj()
      case something => Json.obj("must" -> JsArray(something))
    }

    val shouldObj = should match {
      case Nil       => Json.obj()
      case something => Json.obj("should" -> JsArray(something))
    }

    val filterObj = filter match {
      case Nil       => Json.obj()
      case something => Json.obj("filter" -> JsArray(something))
    }

    val mustNotObj = mustNot match {
      case Nil       => Json.obj()
      case something => Json.obj("must_not" -> JsArray(something))
    }

    Json.obj(
      "bool" -> (mustObj ++ shouldObj ++ filterObj ++ mustNotObj))
  }
}
