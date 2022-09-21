package silvousplay.data

import scala.concurrent.{ ExecutionContext, Future }
import slick.jdbc.meta.MTable
import play.api.Logger

import java.sql.PreparedStatement
import slick.jdbc._
import slick.dbio._
import slick.sql.FixedSqlAction

import silvousplay.data.health._

import slick.lifted
import slick.ast._

trait HealthComponent {
  self: HasProvider with TableComponent =>

  import api._
  import dbConfig.profile.JdbcType

  def all(): List[CanInitializeTable]

  //Cribbed
  //https://github.com/slick/slick/blob/e6640073df/slick/src/main/scala/slick/jdbc/JdbcActionComponent.scala
  type ProfileAction[+R, +S <: NoStream, -E <: Effect] = FixedSqlAction[R, S, E]

  abstract class SimpleJdbcProfileAction[+R](_name: String, val statements: Vector[String]) extends SynchronousDatabaseAction[R, NoStream, JdbcBackend, Effect] with ProfileAction[R, NoStream, Effect] { self =>
    def run(ctx: JdbcBackend#Context, sql: Vector[String]): R
    final override def getDumpInfo = super.getDumpInfo.copy(name = _name)
    final def run(ctx: JdbcBackend#Context): R = run(ctx, statements)
    final def overrideStatements(_statements: Iterable[String]): ProfileAction[R, NoStream, Effect] = new SimpleJdbcProfileAction[R](_name, _statements.toVector) {
      def run(ctx: JdbcBackend#Context, sql: Vector[String]): R = self.run(ctx, statements)
    }
  }

  def profileAction(typ: String)(statements: List[String]): ProfileAction[Unit, NoStream, Effect.Schema] = {
    new SimpleJdbcProfileAction[Unit](s"schema.$typ", statements.toVector) {
      def run(ctx: JdbcBackend#Context, sql: Vector[String]): Unit =
        for (s <- sql) ctx.session.withPreparedStatement(s)(_.execute)
    }
  }

  def checkDatabaseDiff()(implicit ec: ExecutionContext): Future[DatabaseDiff] = {
    checkDatabaseDiff(all())
  }

  def checkDatabaseDiff(tablesIn: List[CanInitializeTable])(implicit ec: ExecutionContext): Future[DatabaseDiff] = {
    for {
      existingTables <- db.run(MTable.getTables(None, Some("public"), None, Some(Seq("TABLE"))))
      allTables = tablesIn.reverse
      existingTablesMap <- {
        val fut = existingTables.map { t =>
          val res = t.getColumns zip t.getImportedKeys zip t.getPrimaryKeys zip t.getIndexInfo()
          db.run(res) map {
            case (((c, fk), pk), i) => {
              t.name.name.toLowerCase -> CurrentTableState(
                t,
                c.toList,
                MultiKeyHelper.groupMultiForeignKeys(fk.toList),
                MultiKeyHelper.groupMultiPrimaryKeys(pk.toList),
                MultiKeyHelper.groupMultiIndexes(i.toList))
            }
          }
        }

        Future.sequence(fut).map(_.toMap)
      }
    } yield {
      val changes = allTables.map { t =>
        val desiredState = DesiredTableState(
          columns = t.columnAlterStatements().map(columnToDesired).toList,
          foreignKeys = t.foreignKeyStatements().map(foreignKeyToDesired).toList,
          primaryKeys = t.primaryKeyStatements().map(primaryKeyToDesired).toList,
          indexes = t.createIndexStatements().map(indexToDesired).toList,
          fullCreate = t.createStatements.toList)

        existingTablesMap.get(t.lowerCaseName) match {
          case Some(currentState) => {
            //Table needs to be altered
            Right(currentState diff desiredState)
          }
          case None => {
            //Table needs to be created
            Left(CreateTable(t.tableName, t.createStatements.toList))
          }
        }
      }.toList

      //1. unused tables
      val allTableNames = allTables.map(_.lowerCaseName).toSet
      val unusedTables = existingTablesMap.filterNot(allTableNames contains _._1).values.map(t => DropTable(t.table.name.name)).toList

      DatabaseDiff(changes.flatMap(_.left.toOption), changes.flatMap(_.toOption), unusedTables)
    }
  }

  def dropDatabase()(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      existingTables <- db.run(MTable.getTables)
      existingTableNames = existingTables.map(_.name.name).toSet
      allTables = all()
      doNotDrop = allTables.filterNot(existingTableNames contains _.tableName)
      _ = {
        if (!doNotDrop.isEmpty) {
          val doNotDropNames = doNotDrop.map(_.tableName).mkString(" ")
          println(s"Did not drop ($doNotDropNames) because they do not exist.")
        }
      }
      drop = allTables.filter(existingTableNames contains _.tableName)
      _ <- {
        if (!drop.isEmpty) {
          val commands = drop.flatMap { table =>
            table.dropStatements
          }
          val droppedNames = drop.map(_.tableName).mkString(" ")
          println(s"Dropped ($droppedNames).")
          commands.foreach(println)
          println(profileAction("drop")(commands))
          db.run {
            profileAction("drop")(commands)
          }
        } else {
          Future.successful(())
        }
      }
    } yield {
      ()
    }
  }

  def ensureDatabase()(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      existingTables <- db.run(MTable.getTables)
      existingTableNames = existingTables.map(_.name.name).toSet
      allTables = all().reverse // dependency order
      doNotCreate = allTables.filter(existingTableNames contains _.tableName)
      _ = {
        if (!doNotCreate.isEmpty) {
          val doNotCreateNames = doNotCreate.map(_.tableName).mkString(" ")
          println(s"Did not create ($doNotCreateNames) because they already exist.")
        }
      }
      create = allTables.filterNot(existingTableNames contains _.tableName)
      _ <- {
        if (!create.isEmpty) {
          val commands = create.flatMap { table =>
            table.createStatements
          }
          val createdNames = create.map(_.tableName)
          createdNames.foreach { name =>
            println(s"Created ($name).")
          }
          db.run {
            profileAction("create")(commands)
          }
        } else {
          Future.successful(())
        }
      }
    } yield {
      ()
    }
  }

  // DDLHelpers

  protected def columnToDesired(input: (FieldSymbol, String)): DesiredColumn = {
    val (c, alter) = input
    val JdbcType(jdbcType, isOption) = c.tpe
    DesiredColumn(c, alter, jdbcType, isOption)
  }

  protected def foreignKeyToDesired(input: (lifted.ForeignKey, String)): DesiredForeignKey = {
    val (fk, alter) = input

    val extractedSourceColumns = KeyHelpers.extractColumns(fk.sourceColumns)

    val extractedTargetColumns = all().flatMap { t =>
      scala.util.Try(t.foreignKeyedColumns(fk)).toOption.map(KeyHelpers.extractColumns)
    }.headOption.getOrElse(throw new Exception("MALFORMED TARGET COLUMN. You probably left out a table in allTables."))

    DesiredForeignKey(fk, extractedSourceColumns, extractedTargetColumns, alter)
  }

  protected def primaryKeyToDesired(input: (lifted.PrimaryKey, String)): DesiredPrimaryKey = {
    val (pk, alter) = input

    val extractedSourceTable = pk.columns.flatMap {
      case s: Select => s.in match {
        case t: TableNode => Some(t.tableName)
        case _            => None
      }
      case _ => None
    }.headOption.getOrElse(throw new Exception("MALFORMED SOURCE TABLE. This should never happen"))

    val extractedSourceColumns = pk.columns.flatMap(KeyHelpers.extractColumns).toList

    DesiredPrimaryKey(pk, extractedSourceTable, extractedSourceColumns, alter)
  }

  protected def indexToDesired(input: (lifted.Index, String)): DesiredIndex = {
    val (idx, alter) = input

    val extractedSourceColumns = idx.on.flatMap(KeyHelpers.extractColumns).toList

    DesiredIndex(idx, extractedSourceColumns, alter)
  }

}

