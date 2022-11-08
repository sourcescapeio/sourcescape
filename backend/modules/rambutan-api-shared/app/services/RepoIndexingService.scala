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
  repoDataService:        LocalRepoDataService,
  gitService:             LocalGitService,
  repoIndexDataService:   RepoIndexDataService,
  gitTreeIndexingService: GitTreeIndexingService,
  clonerService:          ClonerService,
  indexerWorker:          IndexerWorker)(implicit val ec: ExecutionContext, mat: akka.stream.Materializer) {

  /**
   * New method that does everything
   */
  def indexRepo(orgId: Int, directory: String): Future[(Int, List[Int])] = {
    println("INDEXING REPO", directory)
    implicit val spanContext = NoopSpanContext
    for {
      (repo, gitInfo) <- getOrCreateRepo(orgId, directory)
      /**
       * Git info
       */
      maybeDirty = if (gitInfo.isClean) None else Some(gitInfo.statusDiff)
      repoName = repo.repoName
      repoId = repo.repoId
      sha = gitInfo.sha
      missingSHA <- repoIndexDataService.getSHA(repoId, sha).map(_.isEmpty)
      _ <- withFlag(missingSHA) {
        gitTreeIndexingService.updateGitTreeToSha(repo, sha)
      }
      /**
       * Repo refresh direct
       */
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      (indexId, maybeClone) <- maybeDirty match {
        case Some(diff) => {
          getClonerInstructionsForDirty(
            orgId,
            repoName,
            repoId,
            sha,
            diff,
            repo.localPath)
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
      // TODO: yikes this is bad because of language server start/stop
      _ <- Source(indexerItems).mapAsync(1) { item =>
        indexerWorker.runIndex(item)
      }.runWith(Sink.ignore)
      _ <- Source(indexerItems).mapAsync(1) { item =>
        indexerWorker.runLinker(item, Map.empty[String, String])
      }.runWith(Sink.ignore)
    } yield {
      println("COMPLETE")
      (indexId.id, indexerItems.map(_.indexId))
    }
  }

  /**
   * Repo sub methods
   */
  private def getOrCreateRepo(orgId: Int, directory: String) = {
    // check if local repo config exists
    repoDataService.getRepoByPath(orgId, directory).flatMap {
      case Some(v) => {
        Future.successful((v))
        for {
          r <- gitService.getGitRepo(directory)
          repoInfo <- r.getRepoInfo
          _ = r.close()
        } yield {
          (v, repoInfo)
        }
      }
      case None => {
        for {
          r <- gitService.getGitRepo(directory)
          scanResult <- r.scanResult()
          repoInfo <- r.getRepoInfo
          _ = r.close()
          remotes = scanResult.remotes.flatMap { url =>
            val githubHttpsUrl = "https://github.com/([\\w\\.@\\:/\\-~]+).git".r
            val githubGitUrl = "git@github.com:([\\w\\.@\\:/\\-~]+).git".r
            val bitbucketHttpsUrl = "https://[^@]+@bitbucket.org/([\\w\\.@\\:/\\-~]+).git".r
            val bitbucketGitUrl = "git@bitbucket.org:([\\w\\.@\\:/\\-~]+).git".r
            url match {
              case githubHttpsUrl(a)    => Some((a, url, RemoteType.GitHub))
              case githubGitUrl(a)      => Some((a, url, RemoteType.GitHub))
              case bitbucketHttpsUrl(a) => Some((a, url, RemoteType.BitBucket))
              case bitbucketGitUrl(a)   => Some((a, url, RemoteType.BitBucket))
              case _                    => None
            }
          }
          firstRemote = remotes.toList match {
            case head :: Nil => head
            case _           => throw new Exception("invalid remotes for git directory")
          }
          (name, remote, remoteType) = firstRemote
          localRepoConfig = LocalRepoConfig(
            orgId,
            name,
            0,
            scanResult.localDir,
            remote,
            remoteType,
            branches = Nil)
          _ <- repoDataService.upsertRepo(localRepoConfig)
          insertedRepo <- repoDataService.getRepoByPath(orgId, directory).map(_.getOrElse(throw new Exception("failed to insert repo")))
          _ = println(insertedRepo)
        } yield {
          (insertedRepo, repoInfo)
        }
      }
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

  private def getRootId(orgId: Int, repoId: Int, sha: String, includeSelf: Boolean)(implicit context: SpanContext): Future[Option[Int]] = {
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
}
