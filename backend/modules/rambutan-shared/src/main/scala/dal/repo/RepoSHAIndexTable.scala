package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._
import akka.stream.scaladsl.Source

trait RepoSHAIndexTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class RepoSHAIndexTable(tag: Tag) extends Table[RepoSHAIndex](tag, "repo_sha_index") with SafeIndex[RepoSHAIndex] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def repoName = column[String]("repo_name")
    def repoId = column[Int]("repo_id")
    def sha = column[String]("sha")
    def rootShaId = column[Option[Int]]("root_sha_id")
    def dirtySignature = column[Option[JsValue]]("dirty_signature")
    def workId = column[String]("work_id")
    def deleted = column[Boolean]("deleted")
    def created = column[Long]("created")

    def repoIdx = withSafeIndex("repo")(s => index(s, repoId))
    def repoSHAIdx = withSafeIndex("repo_sha")(s => index(s, (repoId, sha)))

    def * = (id, orgId, repoName, repoId, sha, rootShaId, dirtySignature, workId, deleted, created) <> (RepoSHAIndex.tupled, RepoSHAIndex.unapply)
  }

  object RepoSHAIndexTable extends SlickIdDataService[RepoSHAIndexTable, RepoSHAIndex](TableQuery[RepoSHAIndexTable]) {
    object byRepo extends Lookup[Int, List](_.repoId)(_.repoId)

    object byRepoSHA {
      def lookupBatch(repoId: Int, shas: List[String]) = db.run {
        table.filter { i =>
          i.repoId === repoId && i.sha.inSetBind(shas) && i.deleted === false
        }.result
      }
    }

    object Streams {
      def deleted = {
        Source.fromPublisher {
          db.stream {
            table.filter(_.deleted === true).result
          }
        }
      }
    }

    // private object bySHA extends Lookup[String, List](_.sha)(_.sha)
    // object byRepoSha extends CompositeLookup2[Int, String, List](byRepo, bySHA)

    object updateDeletedById extends Updater(byId)(_.deleted)
    object updateRootById extends Updater(byId)(_.rootShaId)
  }
}
