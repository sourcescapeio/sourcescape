package models.query

case class QueryScroll(lastKey: Option[RelationalKey]) {

  def getInitialCursor(ordering: List[String]) = {
    // across Option[T]
    for {
      firstKey <- ordering.headOption
      relationalKey <- lastKey
      r <- relationalKey.leaves.get(firstKey)
    } yield {
      r
    }
  }
}
