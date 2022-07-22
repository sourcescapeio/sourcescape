package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait RepoSHACompilationTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class RepoSHACompilationTable(tag: Tag) extends Table[RepoSHACompilation](tag, "repo_sha_compilation") with SafeIndex[RepoSHACompilation] {
    def shaId = column[Int]("sha_id", O.PrimaryKey)
    def status = column[CompilationStatus]("status")

    def * = (shaId, status) <> (RepoSHACompilation.tupled, RepoSHACompilation.unapply)
  }

  object RepoSHACompilationTable extends SlickDataService[RepoSHACompilationTable, RepoSHACompilation](TableQuery[RepoSHACompilationTable]) {
    // object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
    // private object byRepo extends Lookup[String, List](_.repo)(_.repo)
    // private object bySHA extends Lookup[String, List](_.sha)(_.sha)

    // object byOrgRepo extends CompositeLookup2[Int, String, List](byOrg, byRepo)
    // object byOrgRepoSHA extends CompositeLookup3[Int, String, String, List](byOrg, byRepo, bySHA)
  }
}
