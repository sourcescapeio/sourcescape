package silvousplay.data.health

import slick.jdbc.meta._

case class ColumnToCreate(desired: DesiredColumn) {

  val statement = "ADD COLUMN " + desired.alter

  val createCopyStatement = "ADD COLUMN " + desired.copyAlter

  def updateStatement(tableName: String) = {
    s"""UPDATE "${tableName}" SET "${desired.alternateName}" = CAST("${desired.name}" AS ${desired.jdbcType.sqlTypeName(None)});"""
  }

  val renameStatement = s"""RENAME COLUMN "${desired.alternateName}" TO "${desired.name}""""

}

//TODO: can we consolidate the mismatches?
case class ColumnTypeMismatch(desired: DesiredColumn, actual: MColumn) {

  def errorStatement(tableName: String) = {

    val actualTypes = s""""${actual.name}" ${actual.typeName}"""

    ErrorExplain(
      s"""Bad type for column ${actual.name} on table $tableName. Should be ${desired.jdbcType} but is ${actual.sqlTypeName}.""",
      desired.alter,
      actualTypes)
  }

  val statement = {
    s"""ALTER COLUMN "${actual.name}" SET DATA TYPE ${desired.jdbcType.sqlTypeName(None)}"""
  }
}

case class ColumnNullableMismatch(desired: DesiredColumn, actual: MColumn) {

  val existingIsNull = actual.nullable.getOrElse(true) //postgres defaults to nullable

  val statement = {
    existingIsNull match {
      case true  => s"""ALTER COLUMN "${actual.name}" SET NOT NULL"""
      case false => s"""ALTER COLUMN "${actual.name}" DROP NOT NULL"""
    }
  }
}

case class ColumnAutoIncMismatch(desired: DesiredColumn, actual: MColumn) {

  val existingIsAutoInc = actual.isAutoInc.getOrElse(false) //postgres defaults to not autoinc

  def errorStatement(tableName: String) = {
    val explain = existingIsAutoInc match {
      case true  => s"column ${actual.name} on table $tableName should not be autoinc"
      case false => s"column ${actual.name} on table $tableName should be autoinc"
    }

    val actualAutoInc = s""""${actual.name}" ${actual.typeName} AUTOINC=${actual.isAutoInc}"""

    ErrorExplain(
      explain,
      desired.alter,
      actualAutoInc)
  }
}

case class ColumnToDelete(existing: MColumn) {

  val statement = s"""DROP COLUMN "${existing.name}""""

}

case class TableColumnDiff(
  table:              MTable,
  columnsToCreate:    List[ColumnToCreate],
  mismatchedTypes:    List[ColumnTypeMismatch],
  mismatchedNullable: List[ColumnNullableMismatch],
  mismatchedAutoInc:  List[ColumnAutoIncMismatch],
  columnsToDelete:    List[ColumnToDelete]) extends HasTable {

  //HTML top
  def beforeDeploy: List[String] = List(
    createColumns).flatten

  def afterDeploy: List[String] = List(
    nullableMismatch,
    deleteColumns.toList).flatten

  def errors = ErrorExplainAll(
    errorFix,
    typeMismatchError ++ autoIncMismatchError)

  //HTML explain
  val noChange = {
    columnsToCreate.isEmpty && mismatchedTypes.isEmpty && mismatchedNullable.isEmpty && mismatchedAutoInc.isEmpty && columnsToDelete.isEmpty
  }

  //For testing
  def errorFix: List[String] = {
    val autoIncCreates = mismatchedAutoInc.map(ColumnToCreate apply _.desired)
    val autoIncDeletes = mismatchedAutoInc.map(ColumnToDelete apply _.actual)

    val columnTypeFixes = wrapAlter(mismatchedTypes)(_.statement).toList

    val autoIncFixes = List(
      wrapAlter(autoIncCreates)(_.createCopyStatement), // create copy of column
      autoIncCreates.map(_.updateStatement(tableName)), //copy over data
      wrapAlter(autoIncDeletes)(_.statement), //drop old column
      wrapAlter(autoIncCreates)(_.renameStatement) //rename new column to old column name
    ).flatten

    columnTypeFixes ++ autoIncFixes
  }

  //Helpers
  def createColumns: List[String] = {
    //Ghetto. Cannot multi add columns using single alter in postgres.
    columnsToCreate.flatMap { c =>
      wrapAlter(List(c))(_.statement)
    }
  }

  def nullableMismatch: List[String] = {
    mismatchedNullable.flatMap { c =>
      wrapAlter(List(c))(_.statement)
    }
  }

  def deleteColumns = {
    columnsToDelete.flatMap { c =>
      wrapAlter(List(c))(_.statement)
    }
  }

  //uncorrectable
  def typeMismatchError: List[ErrorExplain] = {
    mismatchedTypes.map(_.errorStatement(tableName))
  }

  def autoIncMismatchError: List[ErrorExplain] = {
    mismatchedAutoInc.map(_.errorStatement(tableName))
  }

}
