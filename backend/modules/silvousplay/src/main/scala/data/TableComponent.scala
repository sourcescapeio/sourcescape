package silvousplay.data

import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.api.Logger

import com.github.tminglei.slickpg._

import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable
import slick.jdbc.JdbcCapabilities
import slick.basic.Capability
import slick.lifted
import slick.ast

import akka.stream.scaladsl.Source

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls
import scala.language.higherKinds

trait PostgresDriver extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgPlayJsonSupport {

  override val pgjson = "jsonb"

  override val api = new API with ArrayImplicits with DateTimeImplicits with PlayJsonImplicits

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate
}

object PostgresDriver extends PostgresDriver

trait HasProvider {
  val dbConfigProvider: DatabaseConfigProvider
  val dbConfig = dbConfigProvider.get[PostgresDriver]
  val db = dbConfig.db
  val api = dbConfig.profile.api
}

trait CanInitializeTable {
  val tableName: String

  lazy val lowerCaseName = tableName.toLowerCase

  def foreignKeyedColumns(fk: lifted.ForeignKey): Any

  // DiffExtensions
  def createStatements: List[String]
  def dropStatements: List[String]
  def foreignKeyStatements(): Map[lifted.ForeignKey, String]
  def createIndexStatements(): Map[lifted.Index, String]
  def columnAlterStatements(): Map[ast.FieldSymbol, String]
  def primaryKeyColumn(): Option[ast.FieldSymbol]
  def primaryKeyStatements(): Map[lifted.PrimaryKey, String]
}

trait TableComponent {
  self: HasProvider =>

  import api._
  import dbConfig.profile.ColumnDDLBuilder

  type HasId[I] = {
    def id: I
  }

  type HasIdTable[I] = {
    def id: Rep[I]
  }

  trait CanInitializeTableImpl[T <: Table[V], V] extends CanInitializeTable {
    val table: TableQuery[T]

    val tableName: String = table.baseTableRow.tableName

    def createStatements = {
      table.schema.create.statements.toList
    }

    def dropStatements = {
      table.schema.drop.statements.toList
    }

    //Index
    val indexes = table.baseTableRow.indexes

    def createIndexStatements(): Map[lifted.Index, String] = {
      createStatements.flatMap { stmt =>
        val isCreate = stmt.startsWith("create ")
        val isIndex = stmt.contains(" index ")
        val maybeIndex = indexes.find(index => stmt.contains(s"""${index.name}"""))

        (isCreate && isIndex, maybeIndex) match {
          case (true, Some(idx)) => Some(idx -> stmt)
          case _                 => None
        }
      }.toMap
    }

    //Columns
    val columns = {
      val star = table.baseTableRow.*.toNode.asInstanceOf[ast.TypeMapping]
      val children = star.child.asInstanceOf[ast.ProductNode].flatten.children
      children.map(c => c.asInstanceOf[ast.Select].field.asInstanceOf[ast.FieldSymbol])
    }
    val columnBuilders: Map[ast.FieldSymbol, ColumnDDLBuilder] = columns.map(m => m -> new ColumnDDLBuilder(m)).toMap

    def columnAlterStatements(): Map[ast.FieldSymbol, String] = {
      columnBuilders map {
        case (k, col) => {
          val sb = new StringBuilder()
          col.appendColumn(sb)
          k -> sb.toString
        }
      }
    }

    //PK
    val primaryKeys = table.baseTableRow.primaryKeys

    def primaryKeyColumn(): Option[ast.FieldSymbol] = columns.find(_.options.contains(ast.ColumnOption.PrimaryKey))

    def primaryKeyStatements(): Map[lifted.PrimaryKey, String] = {
      createStatements.flatMap { stmt =>
        val isAlter = stmt.startsWith("alter table ")
        val isConstraint = stmt.contains(" add constraint ")
        val isPrimary = stmt.contains(" primary key")
        val maybePK = primaryKeys.find(pk => stmt.contains(s""""${pk.name}""""))

        (isAlter && isConstraint && isPrimary, maybePK) match {
          case (true, Some(pk)) => Some(pk -> stmt)
          case _                => None
        }
      }.toMap
    }

    //FK
    def foreignKeyedColumns(fk: lifted.ForeignKey): Any = {
      fk.targetColumns(table.baseTableRow)
    }

    val foreignKeys = table.baseTableRow.foreignKeys

    def foreignKeyStatements(): Map[lifted.ForeignKey, String] = {
      createStatements.flatMap { stmt =>
        val isAlter = stmt.startsWith("alter table ")
        val isConstraint = stmt.contains(" add constraint ")
        val maybeFK = foreignKeys.find(fk => stmt.contains(s""""${fk.name}"""")) // NOTE: this is postgres specific

        (isAlter && isConstraint, maybeFK) match {
          case (true, Some(fk)) => Some(fk -> stmt)
          case _                => None
        }
      }.toMap
    }
  }

  trait FromResult[R[_]] {
    def fromResult[V](items: Iterable[V]): R[V]
  }

  trait SafeIndex[T] {
    self: Table[T] =>

    def withSafeIndex[V](name: String)(f: String => V): V = f(s"${self.tableName}_${name}_idx")

    //TODO: Temp until we figure out the weird type shapes
    def withSafeFK[V](name: String)(f: String => V): V = f(s"${self.tableName}_${name}_fk")

    def withSafePK[V](f: String => V): V = f(s"${self.tableName}_pk")
  }

  implicit val optFromResult = new FromResult[Option] {
    def fromResult[V](items: Iterable[V]): Option[V] = {
      items.headOption
    }
  }

  implicit val listFromResult = new FromResult[List] {
    def fromResult[V](items: Iterable[V]): List[V] = {
      items.toList
    }
  }

  def withTransaction[E <: Effect, S <: NoStream, R](f: => slick.dbio.DBIOAction[R, S, E]): Future[R] = {
    db.run {
      f.transactionally
    }
  }

  abstract class SlickDataService[T <: Table[V], V](
    val table: TableQuery[T]) extends CanInitializeTableImpl[T, V] {

    def insertRaw(item: V)(implicit ec: ExecutionContext): Future[Unit] = {
      db.run {
        (table returning table += item)
      } map (_ => ())
    }

    def insertBulkQuery(items: Seq[V]) = {
      (table returning table ++= items)
    }

    def insertBulk(items: Seq[V])(implicit ec: ExecutionContext): Future[Unit] = {
      db.run {
        insertBulkQuery(items)
      } map (_ => ())
    }

    def insertOrUpdate(item: V)(implicit ec: ExecutionContext): Future[Int] = {
      db.run {
        (table insertOrUpdate item).transactionally
      }
    }

    def insertOrUpdateBatch(items: List[V])(implicit ec: ExecutionContext): Future[Option[Int]] = {
      db.run {
        (table insertOrUpdateAll items).transactionally
      }
    }

    def insertOrUpdateQuery(item: V) = {
      table insertOrUpdate item
    }

    def count()(implicit ec: ExecutionContext): Future[Int] = {
      db.run(table.length.result)
    }

    def stream(limit: Int)(implicit ec: ExecutionContext): Source[V, Any] = {
      Source.fromPublisher(db.stream(table.take(limit).result))
    }

    def all()(implicit ec: ExecutionContext): Future[List[V]] = {
      db.run(table.result).map(_.toList)
    }

    def deleteAll()(implicit ec: ExecutionContext): Future[Unit] = {
      db.run(table.delete) map (_ => ())
    }

    protected class Updater[K, P, UV](val filter: HasQuery[K])(column: T => P)(implicit val shape: Shape[_ <: FlatShapeLevel, P, UV, P]) {

      def updateBatchQuery(ids: List[K], v: UV) = {
        val filtered = filter.queryBatch(ids)
        val mapped = filtered.map[P, P, UV](column)
        mapped.update(v)
      }

      def updateQuery(id: K, v: UV) = {
        val filtered = filter.query(id)
        val mapped = filtered.map[P, P, UV](column)
        mapped.update(v)
      }

      def update(id: K, v: UV)(implicit ec: ExecutionContext): Future[Int] = {
        db.run {
          updateQuery(id, v)
        }
      }

      def updateBatch(ids: List[K], v: UV)(implicit ec: ExecutionContext): Future[Int] = {
        db.run {
          updateBatchQuery(ids, v)
        }
      }
    }

    trait HasQuery[K] {
      type Q = Query[T, V, Seq]

      def queryBatch(ids: Seq[K]): Q

      final def query(id: K) = {
        queryBatch(List(id))
      }
    }

    trait AbstractLookup[K, R[_]] extends HasQuery[K] {
      val fromResult: FromResult[R]
      val getKeyF: V => K

      def lookupCount(id: K)(implicit ec: ExecutionContext): Future[Int] = {
        db.run(query(id).length.result)
      }

      def lookupQuery(id: K) = {
        query(id).result
      }

      def lookupWithFilterQuery(id: K)(f: T => Rep[Boolean]) = {
        query(id).filter(f).result
      }

      def lookup(id: K)(implicit ec: ExecutionContext): Future[R[V]] = {
        db.run(lookupQuery(id)).map(fromResult.fromResult)
      }

      def lookupStream(id: K, limit: Int)(implicit ec: ExecutionContext): Source[V, Any] = {
        Source.fromPublisher(db.stream(query(id).take(limit).result))
      }

      def lookupBatchQuery(ids: Seq[K]) = {
        queryBatch(ids).result
      }

      def lookupBatch(ids: Seq[K])(implicit ec: ExecutionContext): Future[Map[K, R[V]]] = {
        for {
          s <- db.run(lookupBatchQuery(ids))
        } yield {
          s.groupBy(getKeyF).view.mapValues(fromResult.fromResult).toMap
        }
      }

      final def delete(id: K)(implicit ec: ExecutionContext): Future[Int] = {
        deleteBatch(List(id))
      }

      final def deleteQuery(id: K)(implicit ec: ExecutionContext) = {
        deleteBatchQuery(List(id))
      }

      final def deleteBatchQuery(ids: Seq[K]) = {
        queryBatch(ids).delete
      }

      final def deleteBatch(ids: Seq[K])(implicit ec: ExecutionContext): Future[Int] = {
        db.run {
          deleteBatchQuery(ids)
        }
      }
    }

    protected class CompositeLookup2[K1, K2, R[_]](a: Lookup[K1, List], b: Lookup[K2, List])(implicit b1: ast.BaseTypedType[K1], b2: ast.BaseTypedType[K2], val fromResult: FromResult[R]) extends AbstractLookup[(K1, K2), R] {
      val getKeyF = { v =>
        (a.getKeyF(v), b.getKeyF(v))
      }

      def queryBatch(ids: Seq[(K1, K2)]) = {
        ids match {
          case Nil => {
            for {
              r <- table if false
            } yield {
              r
            }
          }
          case nonEmpty => {
            table.filter { i =>
              val ands = ids.map {
                case (aa, bb) => {
                  val aE = (columnExtensionMethods(a.lookupF(i)) === aa)
                  val bE = (columnExtensionMethods(b.lookupF(i)) === bb)

                  aE && bE
                }
              }
              ands.reduceLeft(_ || _)
            }
          }
        }
      }
    }

    protected class CompositeLookup3[K1, K2, K3, R[_]](a: Lookup[K1, List], b: Lookup[K2, List], c: Lookup[K3, List])(implicit b1: ast.BaseTypedType[K1], b2: ast.BaseTypedType[K2], b3: ast.BaseTypedType[K3], val fromResult: FromResult[R]) extends AbstractLookup[(K1, K2, K3), R] {
      val getKeyF = { v =>
        (a.getKeyF(v), b.getKeyF(v), c.getKeyF(v))
      }

      def queryBatch(ids: Seq[(K1, K2, K3)]) = {
        ids match {
          case Nil => {
            for {
              r <- table if false
            } yield {
              r
            }
          }
          case nonEmpty => {
            table.filter { i =>
              val ands = ids.map {
                case (aa, bb, cc) => {
                  val aE = (columnExtensionMethods(a.lookupF(i)) === aa)
                  val bE = (columnExtensionMethods(b.lookupF(i)) === bb)
                  val cE = (columnExtensionMethods(c.lookupF(i)) === cc)

                  aE && bE && cE
                }
              }
              ands.reduceLeft(_ || _)
            }
          }
        }
      }
    }

    protected class Lookup[K, R[_]](val lookupF: T => Rep[K])(val getKeyF: V => K)(implicit b: ast.BaseTypedType[K], val fromResult: FromResult[R]) extends AbstractLookup[K, R] {
      def queryBatch(ids: Seq[K]) = {
        table.filter(i => columnExtensionMethods(lookupF(i)) inSetBind ids)
      }
    }
  }

  abstract class SlickIdDataService[T <: Table[V] with HasIdTable[Int], V <: HasId[Int]](
    table: TableQuery[T])(implicit val b: ast.BaseTypedType[Int]) extends SlickDataService[T, V](table) {

    object byId extends Lookup[Int, Option](_.id)(_.id)

    def insertQuery(item: V) = {
      (table returning table.map(_.id) += item)
    }

    def emptyQuery = Query.empty.result

    def insert(item: V)(implicit ec: ExecutionContext): Future[Int] = {
      db.run {
        insertQuery(item)
      }
    }
  }
}
