package services

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import javax.inject._
import silvousplay.imports._
import models._
import java.nio.file._
import play.api.libs.json._
import org.joda.time._

@Singleton
class WebhookConsumerService @Inject() (
  configuration:               play.api.Configuration,
  webhookConsumerQueueService: WebhookConsumerQueueService,
  queueManagementService:      QueueManagementService,
  gitService:                  LocalGitService,
  socketService:               SocketService,
  repoDataService:             LocalRepoDataService,
  gitTreeIndexingService:      GitTreeIndexingService,
  //
  val indexerService:       IndexerService,
  val repoIndexDataService: RepoIndexDataService,
  val clonerQueueService:   ClonerQueueService,
  val logService:           LogService)(implicit val ec: ExecutionContext) extends ConsumerService {

  val WebhookConcurrency = 4

  def startWebhookConsumer() = {
    for {
      _ <- webhookConsumerQueueService.clearQueue()
      _ <- queueManagementService.runQueue[WebhookConsumerQueueItem](
        "webhook-consumer",
        concurrency = WebhookConcurrency,
        source = webhookConsumerQueueService.source) { item =>
          println("DEQUEUE", item)
          repoRefresh(item)
        } { item =>
          println("COMPLETE + ACK", item)
          webhookConsumerQueueService.ack(item)
        }
    } yield {
      ()
    }
  }

  private def repoRefresh(item: WebhookConsumerQueueItem): Future[Unit] = {
    // refresh button for repo
    // this also looks up stuff for repo
    // just do queue here
    val orgId = item.orgId
    val repoId = item.repoId

    for {
      repo <- repoDataService.getRepo(repoId).map { f =>
        f.getOrElse(throw Errors.notFound("repo.dne", "Repo not found"))
      }
      repoName = repo.repoName
      updatedBranches <- gitTreeIndexingService.updateBranchData(repo)
      info <- gitService.withRepo(repo)(_.getRepoInfo)
      // should get dirty?
      _ <- info.currentBranch match {
        case Some(b) if updatedBranches.contains(b) => {
          // socket update
          for {
            _ <- if (info.isClean) {
              runCleanIndexForSHA(orgId, repoName, repoId, info.sha, forceRoot = false)
            } else {
              runDirtyIndexForSHA(orgId, repoName, repoId, info.sha, info.statusDiff, repo.localPath)
            }
          } yield {
            ()
          }
        }
        case _ => {
          Future.successful {
            println(s"SKIPPING REFRESH at ${info.sha} as it's not on a valid branch.", info.currentBranch)
          }
        }
      }
    } yield {
      ()
    }
  }

  /**
   * Helpers
   */

  // dirty index is completely different path
  private def generateDirtyFiles(localPath: String, statusDiff: GitDiff) = {
    val modified = statusDiff.addPaths.flatMap { p =>
      withFlag(IndexType.all.exists(_.isValidBlob(p))) {
        val bytes = Files.readAllBytes(Paths.get(s"${localPath}/${p}"))
        Option(p -> Hashing.checksum(bytes))
      }
    }.toMap
    // assume non-empty
    ClonerQueueDirty(modified, statusDiff.deleted)
  }

  def runDirtyIndexForSHA(orgId: Int, repoName: String, repoId: Int, sha: String, diff: GitDiff, localPath: String): Future[Either[RepoSHAIndex, (RepoSHAIndex, WorkRecord)]] = {
    val dirtyFiles = generateDirtyFiles(localPath, diff)
    for {
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      // in current indexes, get dirty signature
      dirtySignature = dirtyFiles.signature
      exactMatch = currentIndexes.find(_.compare(dirtySignature))
      res <- exactMatch match {
        case Some(idx) => {
          println("SKIPPED DIRTY", dirtySignature, idx.dirtySignature)
          Future.successful(Left(idx))
        }
        case None => {
          for {
            maybeRootId <- getRootId(orgId, repoId, sha, includeSelf = true)
            parent <- logService.createParent(orgId, Json.obj(
              "repoId" -> repoId,
              "sha" -> sha))
            obj = RepoSHAIndex(0, orgId, repoName, repoId, sha, maybeRootId, Some(Json.toJson(dirtySignature)), parent.id, deleted = false, new DateTime().getMillis())
            index <- repoIndexDataService.writeIndex(obj)(parent)
            item = ClonerQueueItem(orgId, repoId, index.id, Some(dirtyFiles), parent.id)
            _ <- clonerQueueService.enqueue(item)
          } yield {
            Right((index, parent))
          }
        }
      }
    } yield {
      res
    }
  }
}
