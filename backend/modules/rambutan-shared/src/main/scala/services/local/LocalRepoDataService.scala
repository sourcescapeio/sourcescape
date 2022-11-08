package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class LocalRepoDataService @Inject() (
  configuration: play.api.Configuration,
  val dao:       dal.SharedDataAccessLayer,
  localDao:      dal.LocalDataAccessLayer)(implicit val ec: ExecutionContext) extends RepoDataService {

  def getRepoByPath(orgId: Int, directory: String): Future[Option[LocalRepoConfig]] = {
    localDao.LocalRepoConfigTable.byPK.lookup((orgId, directory))
  }

  def getRepoLocal(repoId: Int): Future[Option[LocalRepoConfig]] = {
    localDao.LocalRepoConfigTable.byRepoId.lookup(repoId)
  }

  def getRepo(repoId: Int) = getRepoLocal(repoId)

  def getAllLocalRepos() = localDao.LocalRepoConfigTable.all()
  def getAllRepos() = getAllLocalRepos()

  def getReposForOrg(orgId: Int) = {
    localDao.LocalRepoConfigTable.byOrg.lookup(orgId)
  }

  def getAdditionalOrgs(repoId: Int): Future[List[Int]] = Future.successful(Nil)

  def updateBranches(repoId: Int, branches: List[String]): Future[Unit] = {
    localDao.LocalRepoConfigTable.updateBranchesByRepoId.update(repoId, branches) map (_ => ())
  }

  def upsertRepo(item: LocalRepoConfig): Future[Unit] = {
    localDao.LocalRepoConfigTable.insertRaw(item)
  }
}
