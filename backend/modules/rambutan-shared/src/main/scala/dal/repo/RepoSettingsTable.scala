package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait RepoSettingsTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class RepoSettingsTable(tag: Tag) extends Table[RepoSettings](tag, "repo_settings") with SafeIndex[RepoSettings] {
    def id = column[Int]("repo_id", O.PrimaryKey)
    def intent = column[RepoCollectionIntent]("intent")

    def * = (id, intent) <> (RepoSettings.tupled, RepoSettings.unapply)
  }

  object RepoSettingsTable extends SlickIdDataService[RepoSettingsTable, RepoSettings](TableQuery[RepoSettingsTable]) {
    object updateIntentByRepoId extends Updater(byId)(_.intent)
  }
}
