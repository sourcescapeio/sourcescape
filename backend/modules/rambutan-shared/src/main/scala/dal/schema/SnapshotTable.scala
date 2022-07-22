package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait SnapshotTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class SnapshotTable(tag: Tag) extends Table[Snapshot](tag, "snapshot") with SafeIndex[Snapshot] {
    def schemaId = column[Int]("schema_id")
    def indexId = column[Int]("index_id")
    def workId = column[String]("work_id")
    def status = column[SnapshotStatus]("status")

    def pk = withSafePK(s => primaryKey(s, (schemaId, indexId)))

    def * = (schemaId, indexId, workId, status) <> (Snapshot.tupled, Snapshot.unapply)
  }

  object SnapshotTable extends SlickDataService[SnapshotTable, Snapshot](TableQuery[SnapshotTable]) {
    object bySchema extends Lookup[Int, List](_.schemaId)(_.schemaId)
    object byIndex extends Lookup[Int, List](_.indexId)(_.indexId)

    object byPK extends CompositeLookup2[Int, Int, Option](bySchema, byIndex)
  }
}
