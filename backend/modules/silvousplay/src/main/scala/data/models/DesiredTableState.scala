package silvousplay.data.health

// import com.whil.silvousplay.imports._
import slick.ast._
import slick.jdbc.meta._
import slick.jdbc.JdbcType
import slick.lifted

case class DesiredTableState(
  columns:     List[DesiredColumn],
  foreignKeys: List[DesiredForeignKey],
  primaryKeys: List[DesiredPrimaryKey],
  indexes:     List[DesiredIndex],
  fullCreate:  List[String]) {

  val primaryKeyColumn: Option[DesiredColumn] = columns.find(_.isPK)

}

object Desired {
  def fixQuotes(maybeQuoted: String): String = {
    (maybeQuoted.startsWith("\"") && maybeQuoted.endsWith("\"")) match {
      case true => maybeQuoted
      case _    => s""""$maybeQuoted""""
    }
  }
}
