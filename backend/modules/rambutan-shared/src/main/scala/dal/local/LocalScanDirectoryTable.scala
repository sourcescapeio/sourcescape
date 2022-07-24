package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait LocalScanDirectoryTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class LocalScanDirectoryTable(tag: Tag) extends Table[LocalScanDirectory](tag, "local_scan_directory") with SafeIndex[LocalScanDirectory] {
    def orgId = column[Int]("org_id")
    def scanId = column[Int]("scan_id", O.PrimaryKey, O.AutoInc)
    def path = column[String]("path")

    def * = (orgId, scanId, path) <> (LocalScanDirectory.tupled, LocalScanDirectory.unapply)
  }

  object LocalScanDirectoryTable extends SlickDataService[LocalScanDirectoryTable, LocalScanDirectory](TableQuery[LocalScanDirectoryTable]) {

  }
}
