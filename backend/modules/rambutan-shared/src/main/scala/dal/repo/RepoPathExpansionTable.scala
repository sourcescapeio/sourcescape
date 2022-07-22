package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait RepoPathExpansionTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class RepoPathExpansionTable(tag: Tag) extends Table[RepoPathExpansion](tag, "repo_path_expansion") with SafeIndex[RepoPathExpansion] {
    def userId = column[Int]("user_id")
    def repo = column[String]("repo")
    def path = column[String]("path")

    def pk = withSafePK(s => primaryKey(s, (userId, repo, path)))

    def * = (userId, repo, path) <> (RepoPathExpansion.tupled, RepoPathExpansion.unapply)
  }

  object RepoPathExpansionTable extends SlickDataService[RepoPathExpansionTable, RepoPathExpansion](TableQuery[RepoPathExpansionTable]) {
    private object byUser extends Lookup[Int, List](_.userId)(_.userId)
    private object byRepo extends Lookup[String, List](_.repo)(_.repo)
    private object byPath extends Lookup[String, List](_.path)(_.path)

    object byUserRepo extends CompositeLookup2[Int, String, List](byUser, byRepo)
    object byPK extends CompositeLookup3[Int, String, String, Option](byUser, byRepo, byPath)
  }
}
