package services

import models._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._

trait RepoSyncService {

  implicit val ec: ExecutionContext
  val gitTreeIndexingService: GitTreeIndexingService
  val logService: LogService
  val repoIndexDataService: RepoIndexDataService

  def repoSHARefreshSync(repo: RepoWithSettings, sha: String, maybeDirty: Option[GitDiff]): Future[(RepoSHAIndex, Option[WorkRecord])] = {
    val orgId = repo.repo.orgId
    val repoId = repo.repo.repoId
    val repoName = repo.repo.repoName

    val currentlySkipped = !repo.shouldIndex
    for {
      // scan if we don't have the sha
      missingSHA <- repoIndexDataService.getSHA(repoId, sha).map(_.isEmpty)
      _ <- withFlag(missingSHA) {
        gitTreeIndexingService.updateGitTreeToSha(repo.repo, sha)
      }
      // need to enable queue
      _ <- withFlag(currentlySkipped) {
        setRepoIntent(orgId, repoId, RepoCollectionIntent.Collect, queue = false) map (_ => ())
      }
      // ensure watch
      prelim <- repoRefreshDirect(repo, sha, maybeDirty)
      res <- prelim match {
        case (idx, None) => {
          for {
            workRecord <- logService.getRecord(idx.workId)
            isComplete = workRecord.map(_.status) =?= Some(WorkStatus.Complete)
            maybeRecord = withFlag(!isComplete) {
              workRecord
            }
          } yield {
            (idx, maybeRecord)
          }
        }
        case other => Future.successful(other)
      }
    } yield {
      res
    }
  }

  // Highly specific impl
  def repoRefreshDirect(repo: RepoWithSettings, sha: String, maybeDirty: Option[GitDiff]): Future[(RepoSHAIndex, Option[WorkRecord])]

  // unify interface here for clarity, even though not strictly necessary
  def repoRefreshAsync(orgId: Int, repoId: Int): Future[Unit]

  // TODO: can we move this out?
  def setRepoIntent(orgId: Int, repoId: Int, intent: RepoCollectionIntent, queue: Boolean): Future[Int]
}
