package models.query

import play.api.libs.json._

// Used for scrolling
case class RelationalKeyItem(key: String, path: String, id: String) {

  private def minusOne(str: String) = {
    // we don't need to recurse because id is restricted to
    str.dropRight(1) ++ str.lastOption.map(s => (s - 1).asInstanceOf[Char])
  }

  def searchAfter = Json.obj(
    "search_after" -> List(key, path, minusOne(id)))

  def stringKey = List(key, path, id).mkString("|")
}

object RelationalKeyItem {
  implicit val format = Json.format[RelationalKeyItem]
}

case class RelationalKey(leaves: Map[String, RelationalKeyItem]) {

  private def toStringKey(ordering: List[String], items: Map[String, RelationalKeyItem]) = {
    ordering.map { i =>
      items.getOrElse(i, throw new Exception(s"invalid ordering ${i} ${items}")).stringKey
    }.mkString("#")
  }

  def lte(ordering: List[String], currentLeaves: Map[String, RelationalKeyItem]) = {
    // println(QueryString.stringifyScroll(QueryScroll(Some(RelationalKey(currentLeaves)))))
    // println {
    //   toStringKey(ordering, leaves).compareTo(toStringKey(ordering, currentLeaves))
    // }

    toStringKey(ordering, leaves).compareTo(toStringKey(ordering, currentLeaves)) <= 0
  }
}

object RelationalKey {
  implicit val format = Json.format[RelationalKey]
}
