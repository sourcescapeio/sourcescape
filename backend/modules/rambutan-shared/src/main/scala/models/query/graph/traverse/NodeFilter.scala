package models.query

import silvousplay.imports._
import models.index.NodeType
import models.ESQuery
import play.api.libs.json._
import models.graph.GenericGraphProperty

sealed trait NodeFilter {
  def query: JsObject
}

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

case class NodeIndexFilter(index: Int) extends NodeFilter {
  def query = ESQuery.termSearch("index", index.toString)
}

case class NodeMultiNameFilter(names: List[String]) extends NodeFilter {
  def query = ESQuery.termsSearch("search_name", names)
}

case class NodeNameFilter(nameQuery: String) extends NodeFilter {

  def query = Traverse.extractNameQuery("search_name", nameQuery)

}

case class NodeIdFilter(id: String) extends NodeFilter {
  def query = ESQuery.termSearch("id", id)
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

// this one doesn't do name extraction
case class NodeExactNamesFilter(names: List[String]) extends NodeFilter {

  def query = ESQuery.termsSearch("search_name", names)

}
