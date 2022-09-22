package silvousplay.data.health

import silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class DesiredForeignKey(fk: lifted.ForeignKey, sourceColumns: List[String], targetColumns: List[String], alter: String) {

  val name = fk.name

  val sourceTable = fk.sourceTable.asInstanceOf[TableExpansion].table.asInstanceOf[TableNode]

  val extractedSource = MQName(catalog = None, schema = sourceTable.schemaName, name = sourceTable.tableName)

  val extractedTarget = MQName(catalog = None, schema = fk.targetTable.schemaName, name = fk.targetTable.tableName)

  def nameMatch(other: CurrentForeignKey): Boolean = {
    other.fkName =?= name
  }

  def sourceTableMatch(other: CurrentForeignKey): Boolean = {
    extractedSource.name =?= other.sourceTable.name
  }

  def sourceColumnsMatch(other: CurrentForeignKey): Boolean = {
    sourceColumns.map(Desired.fixQuotes) =?= other.sourceIds.toList.map(Desired.fixQuotes)
  }

  def targetTableMatch(other: CurrentForeignKey): Boolean = {
    extractedTarget.name =?= other.targetTable.name
  }

  def targetColumnsMatch(other: CurrentForeignKey): Boolean = {
    targetColumns.map(Desired.fixQuotes) =?= other.targetIds.toList.map(Desired.fixQuotes)
  }

  def rulesMatch(other: CurrentForeignKey): Boolean = {
    fk.onDelete =?= other.onDelete && fk.onUpdate =?= other.onUpdate
  }

  def structureMismatch(other: CurrentForeignKey): Boolean = {

    val structureMatch = {
      val matchingSourceTable = sourceTableMatch(other)
      val matchingTargetTable = targetTableMatch(other)

      val matchingSource = sourceColumnsMatch(other)
      val matchingTarget = targetColumnsMatch(other)

      val matchingRules = rulesMatch(other)

      matchingSourceTable && matchingSource && matchingTargetTable && matchingTarget && matchingRules
    }

    nameMatch(other) && !structureMatch
  }
}
