package silvousplay.data.health

case class ErrorExplain(
  explain:  String,
  desired:  String,
  existing: String)

case class ErrorExplainAll(
  fixes:    List[String],
  explains: List[ErrorExplain]) {

  def ++(other: ErrorExplainAll) = {
    ErrorExplainAll(
      fixes ++ other.fixes,
      explains ++ other.explains)
  }

  def isEmpty = fixes.isEmpty && explains.isEmpty
}
