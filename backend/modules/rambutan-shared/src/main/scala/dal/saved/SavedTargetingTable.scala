package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait SavedTargetingTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class SavedTargetingTable(tag: Tag) extends Table[SavedTargeting](tag, "saved_targeting") with SafeIndex[SavedTargeting] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def name = column[String]("name")
    def repoId = column[Option[Int]]("repo")
    def branch = column[Option[String]]("branch")
    def sha = column[Option[String]]("sha")
    def fileFilter = column[Option[String]]("file_filter")

    def * = (id, orgId, name, repoId, branch, sha, fileFilter) <> (SavedTargeting.tupled, SavedTargeting.unapply)
  }

  object SavedTargetingTable extends SlickIdDataService[SavedTargetingTable, SavedTargeting](TableQuery[SavedTargetingTable]) {
    object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
  }
}
