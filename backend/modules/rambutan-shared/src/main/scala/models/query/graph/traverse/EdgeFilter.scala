package models.query

import models.ESQuery
import play.api.libs.json._
import models.graph.GenericGraphProperty

sealed trait EdgeFilter {
  def query: JsObject
}

case class EdgeIndexesFilter(idxes: List[Int]) extends EdgeFilter {
  def query = {
    ESQuery.termsSearch("index", idxes.map(_.toString))
  }
}

case class EdgeNamesFilter(names: List[String]) extends EdgeFilter {
  private def isQuery(s: String) = {
    s.contains("*") || (s.startsWith("/") && s.endsWith("/"))
  }

  def query = {
    names match {
      case Nil                                   => ESQuery.matchAll
      case head :: Nil                           => Traverse.extractNameQuery("name", head)
      case rest if rest.forall(s => !isQuery(s)) => ESQuery.termsSearch("name", rest)
      case rest => ESQuery.bool(
        should = rest.map { r =>
          Traverse.extractNameQuery("name", r) // inefficient but simple
        })
    }
  }
}

case class EdgePropsFilter(props: List[GenericGraphProperty]) extends EdgeFilter {
  def query = {
    ESQuery.termsSearch("props", props.map(_.encode))
  }
}
