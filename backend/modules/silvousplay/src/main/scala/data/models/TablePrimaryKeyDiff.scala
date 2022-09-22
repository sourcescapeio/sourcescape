package silvousplay.data.health

import slick.jdbc.meta._

case class PrimaryKeyToCreate(desired: DesiredPrimaryKey) {

  val statement = desired.alter + ";"

}

case class PrimaryKeyColumnToCreate(desired: DesiredColumn) {
  val statement = s"""ADD PRIMARY KEY ("${desired.name}")"""

}

case class PrimaryKeyMismatch(desired: DesiredPrimaryKey, existing: CurrentPrimaryKey) {

  def errorStatement(tableName: String) = {
    val errors = List(
      if (!desired.sourceTableMatch(existing)) Some("SourceTableMismatch") else None,
      if (!desired.sourceColumnsMatch(existing)) Some("SourceColumnsMismatch") else None).flatten.mkString(",")

    val existingDescription = {
      s"""SOURCE = ${existing.table.name}(${existing.ids.mkString(",")})"""
    }

    ErrorExplain(
      s"primary key ${existing.pkName} on table $tableName does not match structure of desired primary key. ($errors)",
      desired.alter,
      existingDescription)
  }
}

case class PrimaryKeyToDelete(existing: CurrentPrimaryKey) {
  val statement = s"""DROP CONSTRAINT "${existing.pkName}""""
}

case class TablePrimaryKeyDiff(
  table:                    MTable,
  primaryKeysToCreate:      List[PrimaryKeyToCreate],
  primaryKeyColumnToCreate: Option[PrimaryKeyColumnToCreate],
  primaryKeyMismatch:       List[PrimaryKeyMismatch],
  primaryKeysToDelete:      List[PrimaryKeyToDelete]) extends HasTable {

  //HTML
  def beforeDeploy: List[String] = {
    List(
      createPrimaryKeys,
      createPrimaryKeyColumn.toList).flatten
  }

  def afterDeploy: List[String] = {
    List(
      deletePrimaryKeys).flatten
  }

  def errors = ErrorExplainAll(
    errorFix,
    mismatchError)

  //Explain
  val noChange = {
    primaryKeysToCreate.isEmpty && primaryKeyColumnToCreate.isEmpty && primaryKeyMismatch.isEmpty && primaryKeysToDelete.isEmpty
  }

  def errorFix = List(
    wrapAlter(primaryKeyMismatch.map(PrimaryKeyToDelete apply _.existing))(_.statement).toList,
    primaryKeyMismatch.map(PrimaryKeyToCreate apply _.desired).map(_.statement)).flatten

  //Helpers
  def createPrimaryKeys: List[String] = {
    primaryKeysToCreate.map(_.statement)
  }

  def createPrimaryKeyColumn: List[String] = {
    wrapAlter(primaryKeyColumnToCreate)(_.statement).toList
  }

  def deletePrimaryKeys = {
    wrapAlter(primaryKeysToDelete)(_.statement)
  }

  //uncorrectable
  def mismatchError: List[ErrorExplain] = {
    primaryKeyMismatch.map(_.errorStatement(table.name.name))
  }

}
