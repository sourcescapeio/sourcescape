package silvousplay.data.health

import slick.jdbc.meta._

case class ForeignKeyToCreate(desired: DesiredForeignKey) {

  val statement = desired.alter + ";"

}

case class ForeignKeyMismatch(desired: DesiredForeignKey, existing: CurrentForeignKey) {

  def errorStatement(tableName: String) = {
    val errors = List(
      if (!desired.sourceTableMatch(existing)) Some("SourceTableMismatch") else None,
      if (!desired.sourceColumnsMatch(existing)) Some("SourceColumnsMismatch") else None,
      if (!desired.targetTableMatch(existing)) Some("TargetTableMismatch") else None,
      if (!desired.targetColumnsMatch(existing)) Some("TargetColumnsMismatch") else None,
      if (!desired.rulesMatch(existing)) Some("RulesMismatch") else None)

    val flattenedErrors = errors.flatten.mkString(",")

    val existingDescription = {
      val source = s"""SOURCE = ${existing.sourceTable.name} (${existing.sourceIds.mkString(",")})"""
      val dest = s"""DEST = ${existing.targetTable.name} (${existing.targetIds.mkString(",")})"""
      val rules = s"RULES = ONUPDATE(${existing.onUpdate}) ONDELETE(${existing.onDelete})"

      source + "\n" + dest + "\n" + rules
    }

    ErrorExplain(
      s"foreign key ${existing.fkName} on table $tableName does not match structure of desired foreign key. ($errors)",
      desired.alter,
      existingDescription)
  }

}

case class ForeignKeyToDelete(existing: CurrentForeignKey) {

  val statement = s"""DROP CONSTRAINT "${existing.fkName}""""

}

case class TableForeignKeyDiff(
  table:               MTable,
  foreignKeysToCreate: List[ForeignKeyToCreate],
  foreignKeysMismatch: List[ForeignKeyMismatch],
  foreignKeysToDelete: List[ForeignKeyToDelete]) extends HasTable {

  //HTML
  def beforeDeploy = createForeignKeys

  def afterDeploy = deleteForeignKeys

  def errors = ErrorExplainAll(
    errorFix,
    mismatchedForeignKeys)

  //HTML explain
  val noChange = {
    foreignKeysToCreate.isEmpty && foreignKeysMismatch.isEmpty && foreignKeysToDelete.isEmpty
  }

  def errorFix = List(
    wrapAlter(foreignKeysMismatch.map(ForeignKeyToDelete apply _.existing))(_.statement).toList,
    foreignKeysMismatch.map(ForeignKeyToCreate apply _.desired).map(_.statement)).flatten

  //Helpers
  def createForeignKeys = {
    foreignKeysToCreate.map(_.statement)
  }

  def deleteForeignKeys = {
    wrapAlter(foreignKeysToDelete)(_.statement)
  }

  //unfixable
  def mismatchedForeignKeys = foreignKeysMismatch.map(_.errorStatement(tableName))

}
