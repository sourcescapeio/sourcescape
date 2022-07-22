package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait SHAIndexTreeTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class SHAIndexTreeTable(tag: Tag) extends Table[SHAIndexTree](tag, "sha_index_tree") with SafeIndex[SHAIndexTree] {
    def indexId = column[Int]("index_id")
    def file = column[String]("file")
    def deleted = column[Boolean]("deleted")

    def pk = withSafePK(s => primaryKey(s, (indexId, file)))
    def shaIdx = withSafeIndex("sha")(s => index(s, indexId))

    def * = (indexId, file, deleted) <> (SHAIndexTree.tupled, SHAIndexTree.unapply)
  }

  object SHAIndexTreeTable extends SlickDataService[SHAIndexTreeTable, SHAIndexTree](TableQuery[SHAIndexTreeTable]) {
    object byIndex extends Lookup[Int, List](_.indexId)(_.indexId)
  }
}
