package silvousplay.data.health

import silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class DesiredPrimaryKey(pk: lifted.PrimaryKey, sourceTable: String, sourceColumns: List[String], alter: String) {

  //val extractedSource = pk.columns.head.
  val name = pk.name

  def nameMatch(other: CurrentPrimaryKey): Boolean = {
    other.pkName =?= name
  }

  def sourceTableMatch(other: CurrentPrimaryKey): Boolean = {
    sourceTable =?= other.tableName
  }

  def sourceColumnsMatch(other: CurrentPrimaryKey): Boolean = {
    sourceColumns.map(Desired.fixQuotes).toList =?= other.ids.map(Desired.fixQuotes).toList
  }

  def structureMismatch(other: CurrentPrimaryKey): Boolean = {
    val structureMatch = {
      val matchingSourceTable = sourceTableMatch(other)
      val matchingSource = sourceColumnsMatch(other)
      matchingSourceTable && matchingSource
    }

    nameMatch(other) && !structureMatch
  }

  def indexMatch(other: CurrentIndex): Boolean = {
    other.indexName =?= name
  }
}
