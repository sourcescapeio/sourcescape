package silvousplay.data.health

// import com.whil.silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.jdbc._
import slick.lifted
import java.sql._

object KeyHelpers {

  def extractColumns(col: Any): List[String] = {
    col match {
      case single: lifted.Rep[_] => single.toNode match {
        case a: OptionApply => { //deal with options
          val select = a.child.asInstanceOf[Select]
          val table = select.in.asInstanceOf[TableNode].tableName
          List(select.field.name)
        }
        case s: Select => List(s.field.name) //all other fields
        case _         => List(single.toString) //other Node subclasses?
      }
      case s: Select => List(s.field.name)
      case other: Product => {
        other.productIterator.flatMap(extractColumns).toList //recurse
      }
      case yes => {
        throw new Exception("WTF")
      }
    }
  }

}

object MultiKeyHelper {

  def groupMultiPrimaryKeys(keys: List[MPrimaryKey]): List[CurrentPrimaryKey] = {
    val grouped = keys.groupBy(_.pkName.getOrElse(""))

    grouped.flatMap {
      case (pkName, allV @ firstV :: _) => {
        val ordered = allV.sortBy(_.keySeq)
        Some(CurrentPrimaryKey(keys, firstV.table, allV.map(_.column), pkName))
      }
      case _ => {
        println("MALFORMED KEY")
        None
      }
    }.toList
  }

  def groupMultiForeignKeys(keys: List[MForeignKey]): List[CurrentForeignKey] = {
    val grouped = keys.groupBy(_.fkName.getOrElse("")) //should be safe

    grouped.flatMap {
      case (pkName, allV @ firstV :: _) => {
        val ordered = allV.sortBy(_.keySeq)

        Some(CurrentForeignKey(allV, firstV.fkTable, ordered.map(_.fkColumn), firstV.pkTable, ordered.map(_.pkColumn), pkName))
      }
      case _ => {
        None //should never occur
      }
    }.toList
  }

  def groupMultiIndexes(indexes: List[MIndexInfo]): List[CurrentIndex] = {
    val grouped = indexes.groupBy(_.indexName.getOrElse(""))

    grouped.flatMap {
      case (indexName, allV @ firstV :: _) => {
        val ordered = allV.sortBy(_.ordinalPosition)

        Some(CurrentIndex(allV, !firstV.nonUnique, firstV.table.name, indexName))
      }
      case _ => None
    }.toList
  }

}
