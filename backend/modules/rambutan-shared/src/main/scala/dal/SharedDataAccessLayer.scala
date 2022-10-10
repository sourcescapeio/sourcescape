package dal

import silvousplay.imports._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

@Singleton
class SharedDataAccessLayer @Inject() (
  val dbConfigProvider: DatabaseConfigProvider)
  extends DataAccessLayer
  // Repos
  with RepoSettingsTableComponent
  with RepoSHATableComponent
  with RepoSHAIndexTableComponent
  //
  with RepoPathExpansionTableComponent
  // trees and pointers
  with AnalysisTreeTableComponent
  // to fix
  with SHAIndexTreeTableComponent
  with RepoSHACompilationTableComponent {

  lazy val allTables = Enums.extract[data.CanInitializeTable]
}
