package silvousplay.data.health

// import silvousplay.imports._
import slick.ast._
import slick.driver.JdbcDriver
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class CurrentTableState(
  table:       MTable,
  columns:     List[MColumn],
  foreignKeys: List[CurrentForeignKey],
  primaryKeys: List[CurrentPrimaryKey],
  indexes:     List[CurrentIndex]) {

  /**
   * Wrapper
   */

  def diff(other: DesiredTableState): AlterTable = {
    val columnIssues = columnDiff(other)
    val foreignKeyIssues = foreignKeyDiff(other)
    val primaryKeyIssues = primaryKeyDiff(other)
    val indexIssues = indexDiff(other)

    //8. Spit out information
    AlterTable(table, this, other, columnIssues, foreignKeyIssues, primaryKeyIssues, indexIssues)
  }

  /**
   * Columns
   */
  def columnDiff(other: DesiredTableState): TableColumnDiff = {
    TableColumnDiff(
      table,
      columnsToCreate(other.columns),
      columnsWithWrongType(other.columns),
      columnsWithWrongNullable(other.columns),
      columnsWithWrongAutoInc(other.columns),
      columnsToDelete(other.columns))
  }

  private def columnsToCreate(desiredColumns: List[DesiredColumn]): List[ColumnToCreate] = {
    desiredColumns.filter(c => !columns.exists(c.nameMatch)).map(ColumnToCreate.apply)
  }

  private def columnsWithWrongType(desiredColumns: List[DesiredColumn]): List[ColumnTypeMismatch] = {
    desiredColumns.flatMap(c => columns.find(c.typeMismatch).map(m => ColumnTypeMismatch(c, m)))
  }

  private def columnsWithWrongNullable(desiredColumns: List[DesiredColumn]): List[ColumnNullableMismatch] = {
    desiredColumns.flatMap(c => columns.flatMap(c.nullableMismatch).map {
      case (desired, existing) => ColumnNullableMismatch(desired, existing)
    })
  }

  private def columnsWithWrongAutoInc(desiredColumns: List[DesiredColumn]): List[ColumnAutoIncMismatch] = {
    desiredColumns.flatMap(c => columns.flatMap(c.autoIncMismatch)).map {
      case (desired, existing) => ColumnAutoIncMismatch(desired, existing)
    }
  }

  private def columnsToDelete(desiredColumns: List[DesiredColumn]): List[ColumnToDelete] = {
    columns.filter(c => !desiredColumns.exists(_.nameMatch(c))).map(ColumnToDelete.apply)
  }

  /**
   * Foreign keys
   */
  def foreignKeyDiff(other: DesiredTableState): TableForeignKeyDiff = {
    TableForeignKeyDiff(
      table,
      foreignKeysToCreate(other.foreignKeys),
      foreignKeysWithMismatch(other.foreignKeys),
      foreignKeysToDelete(other.foreignKeys))
  }

  private def foreignKeysToCreate(desiredKeys: List[DesiredForeignKey]): List[ForeignKeyToCreate] = {
    desiredKeys.filter(k => !foreignKeys.exists(k.nameMatch)).map(ForeignKeyToCreate.apply)
  }

  private def foreignKeysWithMismatch(desiredKeys: List[DesiredForeignKey]): List[ForeignKeyMismatch] = {
    desiredKeys.flatMap(k => foreignKeys.find(k.structureMismatch).map(k -> _)).map {
      case (desired, existing) => ForeignKeyMismatch(desired, existing)
    }
  }

  private def foreignKeysToDelete(desiredKeys: List[DesiredForeignKey]): List[ForeignKeyToDelete] = {
    foreignKeys.filter(k => !desiredKeys.exists(_.nameMatch(k))).map(ForeignKeyToDelete.apply)
  }

  /**
   * Primary keys
   */
  def primaryKeyDiff(other: DesiredTableState): TablePrimaryKeyDiff = {
    TablePrimaryKeyDiff(
      table,
      primaryKeysToCreate(other.primaryKeys),
      primaryKeyColumnToCreate(other.primaryKeyColumn),
      primaryKeysWithMismatch(other.primaryKeys),
      primaryKeysToDelete(other.primaryKeys, other.primaryKeyColumn))
  }

  private def primaryKeysToCreate(desiredKeys: List[DesiredPrimaryKey]): List[PrimaryKeyToCreate] = {
    desiredKeys.filter(p => !primaryKeys.exists(p.nameMatch)).map(PrimaryKeyToCreate.apply)
  }

  private def primaryKeyColumnToCreate(keyColumn: Option[DesiredColumn]): Option[PrimaryKeyColumnToCreate] = {
    keyColumn.filter(p => !primaryKeys.exists(p.primaryKeyMatch)).map(PrimaryKeyColumnToCreate.apply)
  }

  private def primaryKeysWithMismatch(desiredKeys: List[DesiredPrimaryKey]): List[PrimaryKeyMismatch] = {
    desiredKeys.flatMap(k => primaryKeys.find(k.structureMismatch).map(k -> _)).map {
      case (desired, current) => PrimaryKeyMismatch(desired, current)
    }
  }

  private def primaryKeysToDelete(desiredKeys: List[DesiredPrimaryKey], keyColumn: Option[DesiredColumn]): List[PrimaryKeyToDelete] = {
    primaryKeys.filter(p => !desiredKeys.exists(_.nameMatch(p)) && !keyColumn.exists(_.primaryKeyMatch(p))).map(PrimaryKeyToDelete.apply)
  }

  /**
   * Indexes
   */
  def indexDiff(other: DesiredTableState): TableIndexDiff = {
    TableIndexDiff(
      table,
      indexesToCreate(other.indexes),
      indexesWithMismatch(other.indexes),
      indexesToDelete(other.indexes, other.primaryKeys, other.primaryKeyColumn))
  }

  private def indexesToCreate(desiredIndexes: List[DesiredIndex]): List[IndexToCreate] = {
    desiredIndexes.filter(i => !indexes.exists(i.nameMatch)).map(IndexToCreate.apply)
  }

  private def indexesWithMismatch(desiredIndex: List[DesiredIndex]): List[IndexMismatch] = {
    desiredIndex.flatMap(i => indexes.find(i.structureMismatch).map(i -> _)).map {
      case (desired, existing) => IndexMismatch(desired, existing)
    }
  }

  private def indexesToDelete(desiredIndexes: List[DesiredIndex], desiredPrimaryKeys: List[DesiredPrimaryKey], keyColumn: Option[DesiredColumn]): List[IndexToDelete] = {
    indexes.filter(i => !desiredIndexes.exists(_.nameMatch(i)) && !desiredPrimaryKeys.exists(_.indexMatch(i)) && !keyColumn.exists(_.indexMatch(i))).map(IndexToDelete.apply)
  }

}

case class CurrentForeignKey(original: List[MForeignKey], sourceTable: MQName, sourceIds: Iterable[String], targetTable: MQName, targetIds: Iterable[String], fkName: String) {

  val onUpdate = original.head.updateRule

  val onDelete = original.head.deleteRule

}

case class CurrentPrimaryKey(original: List[MPrimaryKey], table: MQName, ids: Iterable[String], pkName: String) {

  val tableName = table.name
}

case class CurrentIndex(original: List[MIndexInfo], unique: Boolean, tableName: String, indexName: String) {
  val columns = original.flatMap(_.column)

}
