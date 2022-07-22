package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait LocalOrgSettingsTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class LocalOrgSettingsTable(tag: Tag) extends Table[LocalOrgSettings](tag, "local_org_settings") with SafeIndex[LocalOrgSettings] {
    def orgId = column[Int]("org_id", O.PrimaryKey)
    def completedNux = column[Boolean]("completed_nux")

    def * = (orgId, completedNux) <> (LocalOrgSettings.tupled, LocalOrgSettings.unapply)
  }

  object LocalOrgSettingsTable extends SlickDataService[LocalOrgSettingsTable, LocalOrgSettings](TableQuery[LocalOrgSettingsTable]) {
    object byOrg extends Lookup[Int, Option](_.orgId)(_.orgId)

    // object updateUserIdByToken extends Updater(byToken)(_.userId)
    // object updateCodeByToken extends Updater(byToken)(_.code)
  }
}
