package silvousplay.data.health

import silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class DesiredColumn(symbol: FieldSymbol, alter: String, jdbcType: JdbcType[Any], isOption: Boolean) {

  val shouldNull = isOption
  // || symbol.options.contains(ColumnOption.Nullable)

  val shouldAutoInc = symbol.options.contains(ColumnOption.AutoInc)

  val isPK = symbol.options.contains(ColumnOption.PrimaryKey)

  val name = symbol.name

  //For fixing bad columns
  val alternateName = symbol.name + "_copy"
  val copyAlter = alter.replaceFirst(name, alternateName).replace(" NOT NULL ", " ")

  def nameMatch(other: MColumn): Boolean = {
    symbol.name.toLowerCase =?= other.name.toLowerCase
  }

  def typeMismatch(other: MColumn): Boolean = {
    val typeMatch = other match {
      case ec if ec.sqlTypeName.map(_.toLowerCase) =?= Some(jdbcType.sqlTypeName(None).toLowerCase) => {
        true
      }
      case ec if (ec.sqlType =?= jdbcType.sqlType) && (jdbcType.sqlType =/= 1111) => {
        //sql types are the same and are not other (1111)
        true
      }
      case ec if ec.typeName =?= jdbcType.sqlTypeName(None) => {
        true
      }
      case ec if (ec.sqlType =?= -7) && (jdbcType.sqlType =?= 16) => {
        //special case: boolean
        true
      }
      case _ => false
    }

    nameMatch(other) && !typeMatch
  }

  def nullableMismatch(other: MColumn): Option[(DesiredColumn, MColumn)] = {
    val nullableMatch = other.nullable =?= Some(shouldNull)
    (nameMatch(other) && !nullableMatch) match {
      case true  => Some(this -> other)
      case false => None //no problem
    }
  }

  def autoIncMismatch(other: MColumn): Option[(DesiredColumn, MColumn)] = {
    val autoincMatch = other.isAutoInc =?= Some(shouldAutoInc)
    (nameMatch(other) && !autoincMatch) match {
      case true  => Some(this -> other)
      case false => None //no problem
    }
  }

  def primaryKeyMatch(other: CurrentPrimaryKey): Boolean = {
    val columnNameMatch = other.ids.toList =?= List(name)
    val pkNameMatch = other.pkName =?= s"${other.table.name}_pkey" //postgres specific

    columnNameMatch && pkNameMatch
  }

  def indexMatch(other: CurrentIndex): Boolean = {
    val nameMatch = other.indexName =?= s"${other.tableName}_pkey"
    val columnMatch = other.columns =?= List(name)

    nameMatch && columnMatch
  }

}
