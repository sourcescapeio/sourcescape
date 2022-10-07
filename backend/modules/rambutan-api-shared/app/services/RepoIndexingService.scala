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
import silvousplay.api.NoopSpanContext
import java.nio.file._
import org.joda.time.DateTime
import workers.IndexerWorker

@Singleton
class RepoIndexingService @Inject() (
  repoDataService:        RepoDataService,
  gitService:             GitService,
  repoIndexDataService:   RepoIndexDataService,
  gitTreeIndexingService: GitTreeIndexingService,
  clonerService:          ClonerService,
  indexerWorker:          IndexerWorker)(implicit val ec: ExecutionContext, mat: akka.stream.Materializer) {

  /**
   * Helpers
   */
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

  protected def getRootId(orgId: Int, repoId: Int, sha: String, includeSelf: Boolean)(implicit context: SpanContext): Future[Option[Int]] = {
    for {
      shaObj <- repoIndexDataService.getSHA(repoId, sha).map {
        _.getOrElse(throw new Exception("sha does not exist"))
      }
      belowChain <- repoIndexDataService.getBelowChain(orgId, repoId, sha).map {
        case items if includeSelf => shaObj :: items
        case items                => items
      }
      aboveChain <- repoIndexDataService.getAboveChain(orgId, repoId, sha)
      // TODO: can add back above chain
      chain = belowChain ++ aboveChain
      chainIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, chain.map(_.sha)).map {
        _.groupBy(_.sha)
      }
      belowRoot = belowChain.flatMap { chainSHA =>
        chainIndexes.getOrElse(chainSHA.sha, Nil).find(_.isRoot)
      }.headOption
      rootIndex = belowRoot.orElse {
        aboveChain.flatMap { chainSHA =>
          chainIndexes.getOrElse(chainSHA.sha, Nil).find(_.isRoot)
        }.headOption
      }
    } yield {
      rootIndex.map(_.id)
    }
  }

  /**
   * Indexing sub methods
   */
  private def getClonerInstructionsForClean(orgId: Int, repoName: String, repoId: Int, sha: String, forceRoot: Boolean)(implicit context: SpanContext): Future[(RepoSHAIndex, Option[ClonerQueueItem])] = {
    for {
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      clean = currentIndexes.find(a => a.isRoot)
      hasClean = clean.isDefined
      diff = currentIndexes.find(a => a.isDiff && !a.dirty)
      maybeRootId <- withFlag(!hasClean && !forceRoot) {
        getRootId(orgId, repoId, sha, includeSelf = false)
      }
      // do queue
      res <- (clean, diff) match {
        case (Some(index), _) => {
          println(s"SKIPPED ${sha} AS WE ALREADY HAVE A ROOT")
          Future.successful {
            (index, None)
          }
        }
        case (_, Some(index)) if maybeRootId.isDefined => {
          println(s"SKIPPED ${sha} AS WE ALREADY HAVE A DIFF")
          Future.successful {
            (index, None)
          }
        }
        case _ => {
          val obj = RepoSHAIndex(0, orgId, repoName, repoId, sha, maybeRootId, dirtySignature = None, "", deleted = false, new DateTime().getMillis())
          for {
            index <- repoIndexDataService.writeIndex(obj)
            item = ClonerQueueItem(orgId, repoId, index.id, None)
          } yield {
            (index, Some(item))
          }
        }
      }
    } yield {
      res
    }
  }

  private def getClonerInstructionsForDirty(orgId: Int, repoName: String, repoId: Int, sha: String, diff: GitDiff, localPath: String)(implicit context: SpanContext): Future[(RepoSHAIndex, Option[ClonerQueueItem])] = {
    val dirtyFiles = generateDirtyFiles(localPath, diff)
    for {
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      // in current indexes, get dirty signature
      dirtySignature = dirtyFiles.signature
      exactMatch = currentIndexes.find(_.compare(dirtySignature))
      res <- exactMatch match {
        case Some(idx) => {
          println("SKIPPED DIRTY", dirtySignature, idx.dirtySignature)
          Future.successful(idx, None)
        }
        case None => {
          for {
            maybeRootId <- getRootId(orgId, repoId, sha, includeSelf = true)
            obj = RepoSHAIndex(0, orgId, repoName, repoId, sha, maybeRootId, Some(Json.toJson(dirtySignature)), "", deleted = false, new DateTime().getMillis())
            index <- repoIndexDataService.writeIndex(obj)
            item = ClonerQueueItem(orgId, repoId, index.id, Some(dirtyFiles))
          } yield {
            (index, Some(item))
          }
        }
      }
    } yield {
      res
    }
  }

  /**
   * New method that does everything
   */
  def indexRepo(orgId: Int, repoId: Int): Future[(Int, List[Int])] = {
    println("INDEXING REPO", orgId, repoId)
    implicit val spanContext = NoopSpanContext
    for {
      repo <- repoDataService.getRepoWithSettings(orgId, repoId).map { f =>
        f.getOrElse(throw Errors.notFound("repo.dne", "Repo not found"))
      }
      /**
       * Git info
       */
      gitInfo <- gitService.withRepo(repo.repo)(_.getRepoInfo)
      maybeDirty = if (gitInfo.isClean) None else Some(gitInfo.statusDiff)
      repoName = repo.repo.repoName
      sha = gitInfo.sha
      missingSHA <- repoIndexDataService.getSHA(repoId, sha).map(_.isEmpty)
      _ <- withFlag(missingSHA) {
        gitTreeIndexingService.updateGitTreeToSha(repo.repo, sha)
      }
      /**
       * Repo refresh direct
       */
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      _ = println(currentIndexes)
      (indexId, maybeClone) <- maybeDirty match {
        case Some(diff) => {
          getClonerInstructionsForDirty(
            orgId,
            repoName,
            repoId,
            sha,
            diff,
            repo.repo.asInstanceOf[LocalRepoConfig].localPath)
        }
        case _ => {
          getClonerInstructionsForClean(orgId, repoName, repoId, sha, forceRoot = false)
        }
      }
      /**
       * Clone sequence
       */
      indexerItems <- withDefined(maybeClone) { cloneItem =>
        println(cloneItem)
        clonerService.runCloneForItem(cloneItem)
      }
      _ = indexerItems.foreach(println)
      _ <- Source(indexerItems).mapAsync(1) { item =>
        indexerWorker.runIndex(item, Map.empty[String, String])
      }.runWith(Sink.ignore)
    } yield {
      println("COMPLETE")
      (indexId.id, indexerItems.map(_.indexId))
    }
  }
}
