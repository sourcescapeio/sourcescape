package models.query

import silvousplay.imports._
import models.index.NodeType
import models.ESQuery
import play.api.libs.json._
import models.graph.GenericGraphProperty

sealed trait NodeFilter {
  def query: JsObject
}

@deprecated
case class NodeTypeFilter(`type`: Identifiable) extends NodeFilter {
  def query = ESQuery.termSearch("type", `type`.identifier)
}

case class NodeTypesFilter(types: List[Identifiable]) extends NodeFilter {
  def query = ESQuery.termsSearch("type", types.map(_.identifier))
}

case class NodeNotTypesFilter(types: List[NodeType]) extends NodeFilter {
  def query = ESQuery.bool(
    mustNot = ESQuery.termsSearch("type", types.map(_.identifier)) :: Nil)
}

@deprecated
case class NodeIndexFilter(index: Int) extends NodeFilter {
  def query = ESQuery.termSearch("index", index.toString)
}

case class NodeIndexesFilter(indexes: List[Int]) extends NodeFilter {
  def query = ESQuery.termsSearch("index", indexes.map(_.toString))
}

@deprecated
case class NodeMultiNameFilter(names: List[String]) extends NodeFilter {
  def query = ESQuery.termsSearch("search_name", names)
}

@deprecated
case class NodeNameFilter(nameQuery: String) extends NodeFilter {

  def query = Traverse.extractNameQuery("search_name", nameQuery)
}

@deprecated
case class NodeExactNamesFilter(names: List[String]) extends NodeFilter {

  def query = ESQuery.termsSearch("search_name", names)

}

case class NodeNamesFilter(names: List[String]) extends NodeFilter {

  private def isQuery(s: String) = {
    s.contains("*") || (s.startsWith("/") && s.endsWith("/"))
  }

  def query = {
    names match {
      case Nil                                   => ESQuery.matchAll
      case head :: Nil                           => Traverse.extractNameQuery("search_name", head)
      case rest if rest.forall(s => !isQuery(s)) => ESQuery.termsSearch("search_name", rest)
      case rest => ESQuery.bool(
        should = rest.map { r =>
          Traverse.extractNameQuery("search_name", r) // inefficient but simple
        })
    }
  }
}

@deprecated
case class NodeIdFilter(id: String) extends NodeFilter {
  def query = ESQuery.termSearch("id", id)
}

case class NodeIdsFilter(ids: List[String]) extends NodeFilter {
  def query = ESQuery.termsSearch("id", ids)
}

case class NodePropsFilter(props: List[GenericGraphProperty]) extends NodeFilter {

  def query = {
    val groupedTerms = props.groupBy(_.key).map {
      case (_, p) => ESQuery.termsSearch("props", p.map(_.encode))
    }

    ESQuery.bool(
      must = groupedTerms.toList)
  }

}
