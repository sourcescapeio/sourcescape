package services

import models.{ AnalysisType, CompilerType, GenericRepo, RepoSHA, WorkRecord, RepoSHAIndex }
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

@Singleton
class ClonerService @Inject() (
  configuration:          play.api.Configuration,
  repoDataService:        RepoDataService,
  repoIndexDataService:   RepoIndexDataService,
  clonerQueueService:     ClonerQueueService,
  indexerQueueService:    IndexerQueueService,
  compilerQueueService:   CompilerQueueService,
  queueManagementService: QueueManagementService,
  logService:             LogService,
  gitService:             GitService,
  socketService:          SocketService,
  fileService:            FileService,
  applicationLifecycle:   play.api.inject.ApplicationLifecycle)(implicit actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) {

  val ClonerConcurrency = 4

  def startCloner() = {
    for {
      _ <- clonerQueueService.clearQueue() // clear queue
      _ <- queueManagementService.runQueue[ClonerQueueItem](
        "clone-repo",
        concurrency = ClonerConcurrency,
        source = clonerQueueService.source) { item =>
          runCloneForItem(item)
        } { item =>
          println("COMPLETE", item)
          Future.successful(())
        }
    } yield {
      ()
    }
  }

  def consumeOne() = {
    for {
      item <- clonerQueueService.source.runWith(Sink.head)
      _ = println(item)
      _ <- runCloneForItem(item)
      // _ <- clonerQueueService.ack(item) // Don't ack?
    } yield {
      ()
    }
  }

  val RequeueSize = 10
  private def runCloneForItem(item: ClonerQueueItem): Future[Unit] = {
    val orgId = item.orgId
    val repoId = item.repoId
    val indexId = item.indexId

    println("CLONE", item)
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
      parent <- logService.upsertParent(orgId, item.workId, Json.obj(
        "repo" -> repoName,
        "repoId" -> repoId,
        "sha" -> itemSHA))
      gitRepo <- gitService.getGitRepo(dbConfig)
      maybeDiff <- withDefined(index.rootIndexId) { rootIndexId =>
        for {
          rootIndex <- repoIndexDataService.getIndexId(rootIndexId) map {
            _.getOrElse(throw new Exception("invalid index"))
          }
          diff <- gitRepo.resolveDiff(rootIndex.sha, itemSHA)
          // if diff is > certain size, queue
          _ = println("DIFF", diff)
          // need to completely requeue
          _ <- withFlag(diff.size > RequeueSize) {
            for {
              newParent <- logService.createParent(orgId, Json.obj(
                "repoId" -> repoId,
                "sha" -> itemSHA))
              obj = RepoSHAIndex(0, orgId, repoName, repoId, itemSHA, None, None, newParent.id, false, new org.joda.time.DateTime().getMillis())
              newIndex <- repoIndexDataService.writeIndex(obj)(parent)
              rootItem = ClonerQueueItem(orgId, repoId, newIndex.id, None, newParent.id)
              _ <- clonerQueueService.enqueue(rootItem)
            } yield {
              ()
            }
          }
        } yield {
          Option(diff)
        }
      }
      fileTree <- gitRepo.getTreeAt(itemSHA)
      _ <- (maybeDiff, item.dirtyFiles) match {
        case (_, Some(dirty)) if !checkDirtyPaths(dirty) => {
          logService.event(s"Dirty file checksums did not match. Likely files changed while item was in the queue. Discarding")(parent)
        }
        case (Some(diff), Some(dirty)) => {
          val addPaths = maybeDiff.map(_.addPaths).getOrElse(Set.empty[String])
          val deleted = maybeDiff.map(_.deleted).getOrElse(Set.empty[String])

          for {
            _ <- logService.event(s"Running dirty index off root")(parent)
            _ <- runClone(
              dbConfig,
              gitRepo,
              index,
              cleanTree = diff.addPaths -- dirty.addPaths,
              dirtyTree = dirty.addPaths,
              deleted = diff.deleted ++ dirty.deleted,
              dirty = true)(parent)
          } yield {
            ()
          }
        }
        case (None, Some(dirty)) => {
          for {
            _ <- logService.event(s"No root. Running a clean root index and then a dirty index")(parent)
            // root
            obj = RepoSHAIndex(0, orgId, repoName, repoId, itemSHA, None, dirtySignature = None, parent.id, deleted = false, new org.joda.time.DateTime().getMillis())
            rootIndex <- repoIndexDataService.writeIndex(obj)(parent)
            _ <- runClone(
              dbConfig,
              gitRepo,
              rootIndex,
              cleanTree = fileTree,
              dirtyTree = Set.empty[String],
              deleted = Set.empty[String],
              dirty = false)(parent)
            // dirty
            _ <- repoIndexDataService.setIndexRoot(indexId, rootIndex.id)
            _ <- runClone(
              dbConfig,
              gitRepo,
              index.copy(rootIndexId = Some(rootIndex.id)),
              cleanTree = Set.empty[String],
              dirtyTree = dirty.addPaths,
              deleted = dirty.deleted,
              dirty = true)(parent)
          } yield {
            ()
          }
        }
        case (Some(diff), None) => {
          for {
            _ <- logService.event(s"Running diffed index")(parent)
            _ <- runClone(
              dbConfig,
              gitRepo,
              index,
              // indexId,
              cleanTree = diff.addPaths,
              dirtyTree = Set.empty[String],
              deleted = diff.deleted,
              dirty = false)(parent)
          } yield {
            ()
          }
        }
        case (None, None) => {
          for {
            _ <- logService.event(s"Running full index of root")(parent)
            _ <- runClone(
              dbConfig,
              gitRepo,
              index,
              cleanTree = fileTree,
              dirtyTree = Set.empty[String],
              deleted = Set.empty[String],
              dirty = false)(parent)
          } yield {
            ()
          }
        }
      }
    } yield {
      gitRepo.close()
      ()
    }
  }

  private def runClone(
    dbRepo: GenericRepo,
    repo:   GitServiceRepo,
    index:  RepoSHAIndex,
    // indexId:         Int,
    cleanTree: Set[String],
    dirtyTree: Set[String],
    deleted:   Set[String],
    dirty:     Boolean)(implicit workRecord: WorkRecord): Future[Unit] = {
    val orgId = dbRepo.orgId
    val repoName = dbRepo.repoName
    val repoId = dbRepo.repoId
    //
    val indexId = index.id
    val rootIndexId = index.rootIndexId
    val sha = index.sha

    for {
      // write index
      // create sub records
      copyRecord <- logService.createChild(workRecord, Json.obj("task" -> "copy"))
      indexRecord <- logService.createChild(workRecord, Json.obj("task" -> "indexing"))
      /**
       * Copy pipeline
       */
      _ <- logService.startRecord(copyRecord)
      additionalOrgIds <- repoDataService.getAdditionalOrgs(repoId)
      _ <- socketService.cloningProgress(orgId, additionalOrgIds, copyRecord.id, repoName, indexId, 0)
      collectionsDirectory = index.collectionsDirectory
      _ <- logService.event(s"Starting copying. Ensuring collections directory ${collectionsDirectory}")(copyRecord)
      _ <- logService.event(s"Copying clean files ${cleanTree.mkString(", ")}")(copyRecord)
      _ <- copyClean(cleanTree.toList, repo, sha, collectionsDirectory)(copyRecord, additionalOrgIds, repoName, indexId)
      _ <- logService.event(s"Copying dirty files ${dirtyTree.mkString(", ")}")(copyRecord)
      _ <- withDefined(dbRepo.dirtyPath) { dirtyPath =>
        copyDirty(dirtyTree.toList, dirtyPath, collectionsDirectory)
      }
      fileTree = cleanTree ++ dirtyTree
      _ <- logService.event(s"Writing out ${fileTree.size} trees")(copyRecord)
      _ <- repoIndexDataService.writeTrees(indexId, fileTree.toList, deleted.toList)
      _ <- logService.event(s"Done copying")(copyRecord)
      _ <- logService.finishRecord(copyRecord)
      //
      compilations = CompilerType.all.filter { ct =>
        fileTree.exists(ct.analysisType.isValidBlob)
      }
      // conditional queue next
      disableCompiles = true
      _ <- if (compilations.isEmpty || disableCompiles) {
        val queueItem = IndexerQueueItem(
          orgId,
          repoName,
          repoId,
          sha,
          indexId,
          fileTree.toList,
          workRecord.id,
          indexRecord.id)
        indexerQueueService.enqueue(queueItem)
      } else {
        for {
          compilerRecord <- logService.createChild(workRecord, Json.obj("task" -> "compile"))
          queueItem = CompilerQueueItem(
            orgId,
            repoName,
            repoId,
            sha,
            indexId,
            fileTree.toList,
            compilations.toList,
            workRecord.id,
            compilerRecord.id,
            indexRecord.id)
          _ <- compilerQueueService.enqueue(queueItem)
        } yield {
          ()
        }
      }
      _ <- socketService.cloningFinished(orgId, additionalOrgIds, copyRecord.id, repoName, indexId)
    } yield {
      ()
    }
  }

  /**
   * Helpers
   */
  private def copyClean(files: List[String], repo: GitServiceRepo, sha: String, to: String)(record: WorkRecord, additionalOrgIds: List[Int], repoName: String, indexId: Int) = {
    ifNonEmpty(files) {
      val fileSet = files.toSet
      for {
        files <- repo.getFilesAt(fileSet, sha)
        fileTotal = fileSet.size
        _ <- Source(files).scanAsync((0, "", akka.util.ByteString.empty)) {
          case ((counter, _, _), (f, content)) => {
            val progress = (counter / fileTotal.toDouble * 100).toInt
            socketService.cloningProgress(record.orgId, additionalOrgIds, record.id, repoName, indexId, progress) map { _ =>
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