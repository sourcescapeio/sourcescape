package silvousplay.data.health

import slick.jdbc.meta._

/**
 * Outputs
 */

case class DatabaseDiff(
  creates: List[CreateTable],
  alters:  List[AlterTable],
  deletes: List[DropTable]) {

  def merge(other: DatabaseDiff) = {
    this.copy(
      this.creates ++ other.creates,
      this.alters ++ other.alters,
      this.deletes ++ other.deletes)
  }

  val altersToChange = alters.filterNot(_.noChange)

  //HTML top
  val beforeDeploy = {
    creates.flatMap(_.displayStatements) ++
      alters.flatMap(_.column.beforeDeploy) ++
      alters.flatMap(_.foreignKey.beforeDeploy) ++
      alters.flatMap(_.primaryKey.beforeDeploy) ++
      alters.flatMap(_.index.beforeDeploy)
  }

  val afterDeploy = {
    deletes.map(_.statement) ++
      alters.flatMap(_.column.afterDeploy) ++
      alters.flatMap(_.foreignKey.afterDeploy) ++
      alters.flatMap(_.primaryKey.afterDeploy) ++
      alters.flatMap(_.index.afterDeploy)
  }

  val errors = {
    alters.foldLeft(ErrorExplainAll(Nil, Nil))(_ ++ _.errors)
  }

  //For testing
  val errorFix: List[String] = errors.fixes

  val allFixes = beforeDeploy ++ afterDeploy ++ errorFix

  //Explains
  val createExplain = creates.map(_.explain)

  val alterExplain = altersToChange.map(_.explainAlter)

  val deleteExplain = deletes.map(_.explain)

  val noChangeExplain = alters.filter(_.noChange).map(_.explain)

  val isEmpty = creates.isEmpty && deletes.isEmpty && alters.forall(_.noChange)

  //Health
  val isHealthy = beforeDeploy.isEmpty && errors.isEmpty

}

case class CreateTable(
  tableName:  String,
  statements: List[String]) {

  val displayStatements = statements map (_ + ";")

  val explain = ExplainTableDiff(
    tableName,
    statements)

}

case class DropTable(
  existing: String) {

  val statement = s"""DROP TABLE public."${existing}" CASCADE;"""

  val explain = ExplainTableDiff(
    existing,
    List(statement))

}

case class AlterTable(
  table:      MTable,
  current:    CurrentTableState,
  desired:    DesiredTableState,
  column:     TableColumnDiff,
  foreignKey: TableForeignKeyDiff,
  primaryKey: TablePrimaryKeyDiff,
  index:      TableIndexDiff) {

  //HTML top
  val errors = {
    column.errors ++ primaryKey.errors ++ foreignKey.errors ++ index.errors
  }

  //Explain
  val noChange: Boolean = {
    column.noChange && foreignKey.noChange && primaryKey.noChange && index.noChange
  }

  val explain = ExplainTableDiff(
    table.name.name,
    desired.fullCreate)

  val allStatements = {
    column.beforeDeploy ++ column.afterDeploy ++ foreignKey.beforeDeploy ++ foreignKey.afterDeploy ++
      primaryKey.beforeDeploy ++ primaryKey.afterDeploy ++ index.beforeDeploy ++ index.afterDeploy
  }

  val allErrors = {
    column.errors ++ foreignKey.errors ++ primaryKey.errors ++ index.errors
  }

  val explainAlter = ExplainAlterTable(
    table.name.name,
    desired.fullCreate,
    allStatements,
    allErrors)

}

trait HasTable {
  val table: MTable

  lazy val tableName = table.name.name
  lazy val AlterTable = s"""ALTER TABLE "$tableName""""

  protected def wrapAlter[T](l: Iterable[T])(f: T => String): Iterable[String] = {
    l match {
      case e if e.isEmpty => Nil
      case _              => l.map(item => AlterTable + " " + f(item) + ";")
    }
  }
}
