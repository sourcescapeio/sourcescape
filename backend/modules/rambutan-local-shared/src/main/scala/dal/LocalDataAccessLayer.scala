package dal

import silvousplay.TSort
import silvousplay.data
import silvousplay.imports._
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

@Singleton
class LocalDataAccessLayer @Inject() (
  val dbConfigProvider: DatabaseConfigProvider)
  extends DataAccessLayer
  // Repos
  with LocalRepoConfigTableComponent
  // Users and Projects
  with OrganizationTableComponent
  with LocalOrgSettingsTableComponent {

  lazy val allTables = Enums.extract[data.CanInitializeTable]
}
