package silvousplay.data.health

case class ExplainTableDiff(
  tableName:  String,
  fullCreate: List[String])

case class ExplainAlterTable(
  tableName:  String,
  fullCreate: List[String],
  alters:     List[String],
  errors:     ErrorExplainAll)
