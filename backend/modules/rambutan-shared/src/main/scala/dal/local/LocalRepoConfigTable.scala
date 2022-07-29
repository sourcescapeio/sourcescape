package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait LocalRepoConfigTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class LocalRepoConfigTable(tag: Tag) extends Table[LocalRepoConfig](tag, "local_repo_config") with SafeIndex[LocalRepoConfig] {
    def orgId = column[Int]("org_id")
    def scanId = column[Int]("scan_id")
    def repoName = column[String]("repo_name")
    def repoId = column[Int]("repo_id", O.AutoInc)
    def localPath = column[String]("local_path")
    def remote = column[String]("remote")
    def remoteType = column[RemoteType]("remote_type")
    def branches = column[List[String]]("branches")
    // def intent = column[Option[RepoCollectionIntent]]("intent")

    def pk = withSafePK(s => primaryKey(s, (orgId, localPath)))
    def orgIdx = withSafeIndex("org")(s => index(s, orgId))
    def repoIdIdx = withSafeIndex("repo_id")(s => index(s, repoId, unique = true))

    def * = (orgId, scanId, repoName, repoId, localPath, remote, remoteType, branches) <> (LocalRepoConfig.tupled, LocalRepoConfig.unapply)
  }

  object LocalRepoConfigTable extends SlickDataService[LocalRepoConfigTable, LocalRepoConfig](TableQuery[LocalRepoConfigTable]) {
    object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
    // private object byRepo extends Lookup[String, List](_.repoName)(_.repoName)
    object byRepoId extends Lookup[Int, Option](_.repoId)(_.repoId)
    object byScanId extends Lookup[Int, List](_.scanId)(_.scanId)

    object updateBranchesByRepoId extends Updater(byRepoId)(_.branches)
  }
}
