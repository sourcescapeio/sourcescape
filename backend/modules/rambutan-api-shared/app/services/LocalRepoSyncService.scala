package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import java.util.Base64
import akka.stream.scaladsl.{ Source, Sink }
import silvousplay.api.SpanContext

@Singleton
class LocalRepoSyncService @Inject() (
  configuration:               play.api.Configuration,
  watcherService:              WatcherService,
  socketService:               SocketService,
  webhookConsumerQueueService: WebhookConsumerQueueService,
  webhookConsumerService:      WebhookConsumerService,
  gitService:                  LocalGitService,
  repoDataService:             LocalRepoDataService,
  val repoIndexDataService:    RepoIndexDataService,
  val logService:              LogService,
  val gitTreeIndexingService:  GitTreeIndexingService)(implicit val ec: ExecutionContext, mat: akka.stream.Materializer) extends RepoSyncService {

  lazy val UseWatcher = configuration.get[Boolean]("use.watcher")

  /**
   * Watcher adjustments
   */
  def syncAllWatches() = {
    for {
      _ <- watcherService.disconnectAll()
      items <- repoDataService.getAllRepoWithSettings()
      // we're clearing everything so no need to DELETE watches
      _ <- Source(items.filter(_.shouldIndex)).mapAsync(4) { repo =>
        for {
          _ <- refreshRepoWithoutRootIndex(repo.repo.orgId, repo.repo.repoId)
          _ <- watcherService.syncWatchForRepo(repo)
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      // then do queue
    } yield {
      ()
    }
  }

  private def syncWatch(orgId: Int, repoId: Int, queue: Boolean) = {
    for {
      localRepo <- repoDataService.getRepoWithSettings(orgId, repoId).map(_.getOrElse {
        throw Errors.notFound("repo.dne", "Repo not found")
      })
      _ <- withFlag(UseWatcher) {
        watcherService.syncWatchForRepo(localRepo)
      }
      // then do queue
    } yield {
      ()
    }
  }

  private def refreshRepoWithoutRootIndex(orgId: Int, repoId: Int) = {
    for {
      indexes <- repoIndexDataService.getIndexesForRepo(repoId)
      hasRoot = indexes.exists(_.isRoot)
      _ <- withFlag(!hasRoot) {
        repoRefreshAsync(orgId, repoId)
      }
    } yield {
      ()
    }
  }

  /**
   * Webhook facing
   */
  override def repoRefreshAsync(orgId: Int, repoId: Int): Future[Unit] = {
    // refresh button for repo
    // this also looks up stuff for repo
    println("REPO REFRESH", repoId)
    // just do queue here
    val item = WebhookConsumerQueueItem(orgId, repoId)
    webhookConsumerQueueService.enqueue(item)
  }

  /**
   * RepoSyncService overrides
   */
  override def repoRefreshDirect(repo: RepoWithSettings, sha: String, maybeDirty: Option[GitDiff])(implicit context: SpanContext): Future[(RepoSHAIndex, Option[WorkRecord])] = {
    val orgId = repo.repo.orgId
    val repoName = repo.repo.repoName
    val repoId = repo.repo.repoId
    maybeDirty match {
      case Some(statusDiff) => {
        webhookConsumerService.runDirtyIndexForSHA(
          orgId,
          repoName,
          repoId,
          sha,
          statusDiff,
          repo.repo.asInstanceOf[LocalRepoConfig].localPath) map {
            case Left(idx)              => (idx, None)
            case Right((index, record)) => (index, Some(record))
          }
      }
      case None => {
        // clean
        webhookConsumerService.runCleanIndexForSHA(orgId, repoName, repoId, sha, forceRoot = false) map {
          case Left(idx)          => (idx, None)
          case Right((idx, work)) => (idx, Some(work))
        }
      }
    }
  }

  def setRepoIntent(orgId: Int, repoId: Int, intent: RepoCollectionIntent, queue: Boolean): Future[Int] = {
    for {
      // get repo
      changed <- repoDataService.setRepoIntent(orgId, repoId, intent)
      // need to get repo
      setCollect = (intent =?= RepoCollectionIntent.Collect)
      _ <- withFlag(changed > 0 && queue && setCollect) {
        refreshRepoWithoutRootIndex(orgId, repoId)
      }
      _ <- withFlag(changed > 0) {
        syncWatch(orgId, repoId, queue)
      }
      // queue repo
      _ <- withFlag(changed > 0) {
        socketService.reposUpdated(orgId)
      }
    } yield {
      changed
    }
  }
}
