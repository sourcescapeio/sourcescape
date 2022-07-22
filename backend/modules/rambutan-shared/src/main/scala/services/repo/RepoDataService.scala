package services

import models._
import scala.concurrent.{ ExecutionContext, Future }

trait RepoDataService {

  val dao: dal.SharedDataAccessLayer
  implicit val ec: ExecutionContext

  def getRepo(repoId: Int): Future[Option[GenericRepo]]

  def getAllRepos(): Future[List[GenericRepo]]

  def getReposForOrg(orgId: Int): Future[List[GenericRepo]]

  def getRepoWithSettings(orgId: Int, repoId: Int): Future[Option[RepoWithSettings]] = {
    for {
      repos <- getRepo(repoId)
      hydrated <- hydrateWithSettings(repos.toList)
    } yield {
      hydrated.headOption
    }
  }

  def getAllRepoWithSettings(): Future[List[RepoWithSettings]] = {
    for {
      repos <- getAllRepos()
      hydrated <- hydrateWithSettings(repos)
    } yield {
      hydrated
    }
  }

  def getRepoWithSettingsForOrg(orgId: Int): Future[List[RepoWithSettings]] = {
    for {
      repos <- getReposForOrg(orgId)
      hydrated <- hydrateWithSettings(repos)
    } yield {
      hydrated
    }
  }

  //
  def getAdditionalOrgs(repoId: Int): Future[List[Int]]

  def updateBranches(repoId: Int, branches: List[String]): Future[Unit]

  /**
   * Repo Settings
   */
  def getRepoSettings(repoIds: List[Int]): Future[Map[Int, Option[RepoSettings]]] = {
    dao.RepoSettingsTable.byId.lookupBatch(repoIds)
  }

  def getRepoSetting(repoId: Int): Future[Option[RepoSettings]] = {
    dao.RepoSettingsTable.byId.lookup(repoId)
  }

  def setRepoIntent(orgId: Int, repoId: Int, intent: RepoCollectionIntent): Future[Int] = {
    dao.RepoSettingsTable.insertOrUpdate(RepoSettings(repoId, intent))
  }

  def hydrateWithSettings(repos: List[GenericRepo]): Future[List[RepoWithSettings]] = {
    val uniqueSet = repos.groupBy(_.repoName).flatMap {
      case (_, vs) if vs.length > 1 => None
      case (k, _)                   => Some(k)
    }.toSet

    for {
      settingsMap <- dao.RepoSettingsTable.byId.lookupBatch(repos.map(_.repoId))
    } yield {
      repos.map { repo =>
        repo.withSettings(uniqueSet, settingsMap)
      }
    }
  }
}
