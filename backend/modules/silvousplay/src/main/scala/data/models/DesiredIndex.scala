package silvousplay.data.health

import silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class DesiredIndex(idx: lifted.Index, sourceColumns: List[String], alter: String) {

  val name = idx.name

  def nameMatch(other: CurrentIndex): Boolean = {
    other.indexName =?= name
  }

  def uniquenessMatch(other: CurrentIndex) = other.unique =?= idx.unique
  def columnsMatch(other: CurrentIndex) = other.columns.map(Desired.fixQuotes) =?= sourceColumns.map(Desired.fixQuotes)
  def tablesMatch(other: CurrentIndex) = other.tableName =?= idx.table.tableName

  def structureMismatch(other: CurrentIndex): Boolean = {
    val matchUnique = uniquenessMatch(other)
    val matchColumns = columnsMatch(other)
    val matchTable = tablesMatch(other)

    nameMatch(other) && !(matchUnique && matchColumns && matchTable)
  }

}
