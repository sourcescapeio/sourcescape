package silvousplay.data.health

import slick.jdbc.meta._

case class IndexToCreate(desired: DesiredIndex) {
  val statement = desired.alter + ";"
}

case class IndexMismatch(desired: DesiredIndex, existing: CurrentIndex) {

  def errorStatement(tableName: String) = {
    val errors = List(
      if (!desired.tablesMatch(existing)) Some("TableMismatch") else None,
      if (!desired.columnsMatch(existing)) Some("ColumnsMismatch") else None,
      if (!desired.uniquenessMatch(existing)) Some("UniquenessMismatch") else None).flatten.mkString(",")

    val existingDescription = {
      s"""SOURCE = ${existing.tableName}(${existing.columns.mkString(",")})"""
    }

    ErrorExplain(
      s"index ${existing.indexName} on table $tableName does not match structure of desired index. ($errors): $desired $existing",
      desired.alter,
      existingDescription)
  }
}

case class IndexToDelete(existing: CurrentIndex) {
  val statement = s"""ALTER TABLE "${existing.tableName}" DROP CONSTRAINT "${existing.indexName}";"""
}

case class TableIndexDiff(
  table:           MTable,
  indexesToCreate: List[IndexToCreate],
  indexesMismatch: List[IndexMismatch],
  indexesToDelete: List[IndexToDelete]) extends HasTable {

  //HTML
  def beforeDeploy = createIndexes
  def afterDeploy = deleteIndexes
  def errors = ErrorExplainAll(
    errorFix,
    mismatchedIndexes)

  //Explain
  val noChange = {
    indexesToCreate.isEmpty && indexesMismatch.isEmpty && indexesToDelete.isEmpty
  }

  def errorFix = List(
    indexesMismatch.map(IndexToDelete apply _.existing).map(_.statement),
    indexesMismatch.map(IndexToCreate apply _.desired).map(_.statement)).flatten

  //Helpers
  def createIndexes = indexesToCreate map (_.statement)
  def deleteIndexes = indexesToDelete map (_.statement)

  def mismatchedIndexes = indexesMismatch map (_.errorStatement(tableName))

}
