package services

import models.{ GenericRepo, RepoSHA, RepoSHAIndex }
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, SourceQueue, Sink }
import akka.stream.{ OverflowStrategy, CompletionStrategy }
import akka.actor.{ Actor, Props, ActorRef }
import akka.pattern.{ ask, pipe }

// Files
import org.apache.commons.io.FileUtils
import java.nio.file.{ Paths, Files }
import java.io.File
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.api.Git
import silvousplay.api.SpanContext

@Singleton
class ClonerService @Inject() (
  configuration:        play.api.Configuration,
  repoDataService:      RepoDataService,
  repoIndexDataService: RepoIndexDataService,
  indexerQueueService:  IndexerQueueService,
  gitService:           GitService,
  socketService:        SocketService,
  fileService:          FileService)(implicit actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) {

  val RequeueSize = 10
  def runCloneForItem(item: ClonerQueueItem)(implicit context: SpanContext): Future[List[IndexerQueueItem]] = {
    val orgId = item.orgId
    val repoId = item.repoId
    val indexId = item.indexId

    for {
      dbConfig <- repoDataService.getRepo(repoId).map {
        _.getOrElse(throw new Exception("invalid repo"))
      }
      index <- repoIndexDataService.getIndexId(indexId).map {
        _.getOrElse(throw new Exception("invalid index"))
      }
      itemSHA = index.sha
      // get index
      repoName = dbConfig.repoName
      runCloneContext = context.decoupledSpan("Running clone")
      gitRepo <- gitService.getGitRepo(dbConfig)
      maybeDiff <- withDefined(index.rootIndexId) { rootIndexId =>
        for {
          rootIndex <- repoIndexDataService.getIndexId(rootIndexId) map {
            _.getOrElse(throw new Exception("invalid index"))
          }
          diff <- gitRepo.resolveDiff(rootIndex.sha, itemSHA)
        } yield {
          Option(diff)
        }
      }
      fileTree <- gitRepo.getTreeAt(itemSHA)
      indexerItems <- (maybeDiff, item.dirtyFiles) match {
        case (_, Some(dirty)) if !checkDirtyPaths(dirty) => {
          runCloneContext.event(s"Dirty file checksums did not match. Likely files changed while item was in the queue. Discarding")
          Future.successful(Nil)
        }
        case (Some(diff), Some(dirty)) => {
          val addPaths = maybeDiff.map(_.addPaths).getOrElse(Set.empty[String])
          val deleted = maybeDiff.map(_.deleted).getOrElse(Set.empty[String])

          runCloneContext.event(s"Running dirty index off root")
          for {
            indexerItem <- runSingleClone(
              dbConfig,
              gitRepo,
              index,
              cleanTree = diff.addPaths -- dirty.addPaths,
              dirtyTree = dirty.addPaths,
              deleted = diff.deleted ++ dirty.deleted,
              dirty = true)(runCloneContext)
          } yield {
            List(indexerItem)
          }
        }
        case (None, Some(dirty)) => {
          runCloneContext.event(s"No root. Running a clean root index and then a dirty index")
          val obj = RepoSHAIndex(0, orgId, repoName, repoId, itemSHA, None, dirtySignature = None, "", deleted = false, new org.joda.time.DateTime().getMillis())
          for {
            // root
            rootIndex <- repoIndexDataService.writeIndex(obj)
            indexerItemClean <- runSingleClone(
              dbConfig,
              gitRepo,
              rootIndex,
              cleanTree = fileTree,
              dirtyTree = Set.empty[String],
              deleted = Set.empty[String],
              dirty = false)(runCloneContext)
            // dirty
            _ <- repoIndexDataService.setIndexRoot(indexId, rootIndex.id)
            indexerItem <- runSingleClone(
              dbConfig,
              gitRepo,
              index.copy(rootIndexId = Some(rootIndex.id)),
              cleanTree = Set.empty[String],
              dirtyTree = dirty.addPaths,
              deleted = dirty.deleted,
              dirty = true)(runCloneContext)
          } yield {
            List(indexerItem, indexerItemClean)
          }
        }
        case (Some(diff), None) => {
          runCloneContext.event(s"Running diffed index")
          for {
            indexerItem <- runSingleClone(
              dbConfig,
              gitRepo,
              index,
              // indexId,
              cleanTree = diff.addPaths,
              dirtyTree = Set.empty[String],
              deleted = diff.deleted,
              dirty = false)(runCloneContext)
          } yield {
            List(indexerItem)
          }
        }
        case (None, None) => {
          runCloneContext.event(s"Running full index of root")
          for {
            indexerItem <- runSingleClone(
              dbConfig,
              gitRepo,
              index,
              cleanTree = fileTree,
              dirtyTree = Set.empty[String],
              deleted = Set.empty[String],
              dirty = false)(runCloneContext)
          } yield {
            List(indexerItem)
          }
        }
      }
    } yield {
      runCloneContext.terminate()
      gitRepo.close()
      indexerItems
    }
  }

  private def runSingleClone(
    dbRepo: GenericRepo,
    repo:   GitServiceRepo,
    index:  RepoSHAIndex,
    // indexId:         Int,
    cleanTree: Set[String],
    dirtyTree: Set[String],
    deleted:   Set[String],
    dirty:     Boolean)(context: SpanContext): Future[IndexerQueueItem] = {
    context.withSpan("Doing clone...") { implicit subContext =>
      val orgId = dbRepo.orgId
      val repoName = dbRepo.repoName
      val repoId = dbRepo.repoId
      //
      val indexId = index.id
      val rootIndexId = index.rootIndexId
      val sha = index.sha

      for {
        /**
         * Copy pipeline
         */
        _ <- socketService.cloningProgress(orgId, repoName, repoId, indexId, 0)
        collectionsDirectory = index.collectionsDirectory
        _ = subContext.event(s"Starting copying. Ensuring collections directory ${collectionsDirectory}")
        _ = subContext.event(s"Copying clean files ${cleanTree.mkString(", ")}")
        _ <- copyClean(orgId, cleanTree.toList, repo, sha, collectionsDirectory)(repoName, repoId, indexId)
        _ = subContext.event(s"Copying dirty files ${dirtyTree.mkString(", ")}")
        _ <- withDefined(dbRepo.dirtyPath) { dirtyPath =>
          copyDirty(dirtyTree.toList, dirtyPath, collectionsDirectory)
        }
        fileTree = cleanTree ++ dirtyTree
        _ = subContext.event(s"Writing out ${fileTree.size} trees")
        _ <- repoIndexDataService.writeTrees(indexId, fileTree.toList, deleted.toList)
        _ = subContext.event(s"Done copying")
        //
        queueItem = IndexerQueueItem(
          orgId,
          repoName,
          repoId,
          sha,
          indexId,
          fileTree.toList)
        _ <- socketService.cloningFinished(orgId, repoName, repoId, indexId)
      } yield {
        queueItem
      }
    }
  }

  /**
   * Helpers
   */
  private def copyClean(orgId: Int, files: List[String], repo: GitServiceRepo, sha: String, to: String)(repoName: String, repoId: Int, indexId: Int) = {
    ifNonEmpty(files) {
      val fileSet = files.toSet
      for {
        files <- repo.getFilesAt(fileSet, sha)
        fileTotal = fileSet.size
        _ <- Source(files).scanAsync((0, "", akka.util.ByteString.empty)) {
          case ((counter, _, _), (f, content)) => {
            val progress = (counter / fileTotal.toDouble * 100).toInt
            socketService.cloningProgress(orgId, repoName, repoId, indexId, progress) map { _ =>
              (counter + 1, f, content)
            }
          }
        }.drop(1).mapAsync(fileService.parallelism) { // drop 1 to get rid of sync initial
          case (c, f, content) => {
            val toFile = s"${to}/${f}"
            fileService.writeFile(toFile, content)
          }
        }.runWith(Sink.ignore)
      } yield {
        ()
      }
    }
  }

  private def copyDirty(files: List[String], from: String, to: String) = {
    ifNonEmpty(files) {
      Source(files).mapAsync(2) { f =>
        Future {
          val fromFile = s"${from}/${f}"
          val toFile = s"${to}/${f}"

          FileUtils.copyFile(new File(fromFile), new File(toFile), true)
        }
      }.runWith(Sink.ignore).map(_ => ())
    }
  }

  private def checkDirtyPaths(dirty: ClonerQueueDirty) = {
    println("CHECKING", dirty)
    val modifiedOkay = dirty.modified.forall {
      case (f, checksum) if Files.exists(Paths.get(f)) => {
        val currentChecksum = Hashing.checksum(Files.readAllBytes(Paths.get(f)))
        println("CHECKSUM", checksum, currentChecksum)
        checksum =?= currentChecksum
      }
      case _ => false
    }
    val deletedOkay = dirty.deleted.forall { d =>
      !Files.exists(Paths.get(d))
    }
    deletedOkay
  }

}