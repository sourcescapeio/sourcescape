package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait RepoSHATableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class RepoSHATable(tag: Tag) extends Table[RepoSHA](tag, "repo_sha") with SafeIndex[RepoSHA] {
    def repoId = column[Int]("repo_id")
    def sha = column[String]("sha")
    def parents = column[List[String]]("parents")
    def branches = column[List[String]]("branches")
    def refs = column[List[String]]("refs")
    def message = column[String]("message")

    def pk = withSafePK(s => primaryKey(s, (repoId, sha)))

    def * = (repoId, sha, parents, branches, refs, message) <> (RepoSHA.tupled, RepoSHA.unapply)
  }

  object RepoSHATable extends SlickDataService[RepoSHATable, RepoSHA](TableQuery[RepoSHATable]) {
    // object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
    object byRepo extends Lookup[Int, List](_.repoId)(_.repoId)
    private object bySHA extends Lookup[String, List](_.sha)(_.sha)

    object byRepoBranch {
      def lookup(repoId: Int, branch: String) = db.run {
        table.filter { i =>
          i.repoId === repoId && branch.bind === i.branches.any
        }.result
      }
    }

    object byPK extends CompositeLookup2[Int, String, Option](byRepo, bySHA)
  }
}
