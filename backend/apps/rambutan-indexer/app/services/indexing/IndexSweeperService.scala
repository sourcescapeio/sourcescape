package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import models.UnifiedRepoSummary
import akka.stream.scaladsl.{ Source, Sink }
import org.joda.time.DateTime
import silvousplay.imports._

@Singleton
class IndexSweeperService @Inject() (
  configuration:          play.api.Configuration,
  dao:                    dal.SharedDataAccessLayer, // eww
  indexService:           IndexService,
  repoIndexDataService:   RepoIndexDataService,
  repoDataService:        RepoDataService,
  cronService:            CronService,
  logService:             LogService,
  socketService:          SocketService,
  fileService:            FileService,
  queueManagementService: QueueManagementService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val SweeperConcurrency = 1
  val SweeperTick = 30.minute
  def startSweeper() = {
    queueManagementService.runQueue[RepoWithSettings](
      "index-sweeper-cron",
      concurrency = SweeperConcurrency,
      source = cronService.source(SweeperTick) {
        println("RUNNING INDEX SWEEPER", new DateTime())
        repoDataService.getAllRepoWithSettings()
      }) { item =>
        // println("DEQUEUE", item)
        sweepRepo(item)
      } { item =>
        // println("COMPLETE", item)
        Future.successful(())
      }
  }

  def startDeletion() = {
    queueManagementService.runQueue[RepoSHAIndex](
      "index-deletion-cron",
      concurrency = SweeperConcurrency,
      source = cronService.sourceFlatMap(SweeperTick) {
        println("RUNNING INDEX DELETION", new DateTime())
        repoIndexDataService.getDeletedIndexes()
      }) { item =>
        // println("DEQUEUE", item)
        runIndexDeletion(item)
      } { item =>
        // println("COMPLETE", item)
        Future.successful(())
      }
  }

  /**
   * Actions
   */
  private def sweepRepo(repo: RepoWithSettings): Future[Unit] = {
    for {
      // get all indexes
      // get shas
      indexes <- repoIndexDataService.getIndexesForRepo(repo.repo.repoId)
      shas = indexes.map(_.sha).distinct
      indexMap = indexes.groupBy(_.sha)
      _ <- Source(shas).map { sha =>
        val shaIndexes = indexMap.getOrElse(sha, Nil)
        val hasRoot = shaIndexes.exists(_.isRoot)

        // When to decide to delete entire SHA?
        // When to decide to delete
        val sweepDirty = shaIndexes.filter(_.dirty).sortBy(_.created).dropRight(1) // drop all but latest
        if (hasRoot) {
          val sweepCleanDiff = shaIndexes.filter(i => i.isDiff && !i.dirty)
          sweepCleanDiff ++ sweepDirty
        } else {
          sweepDirty
        }
      }.mapConcat(i => i).mapAsync(1) { index =>
        repoIndexDataService.markIndexDeleted(index.id)
      }.runWith(Sink.ignore)
    } yield {
      ()
    }
  }

  /**
   * Deletion actions
   */
  private def runIndexDeletion(index: RepoSHAIndex): Future[Unit] = {
    val orgId = index.orgId
    val repoId = index.repoId
    val indexId = index.id
    println("Deleting", indexId)
    for {
      _ <- repoIndexDataService.deleteAnalysisTrees(indexId)
      _ <- dao.SHAIndexTreeTable.byIndex.delete(indexId)
      _ <- dao.RepoSHAIndexTable.byId.delete(indexId)
      _ <- logService.deleteWork(orgId, index.workId)
      _ <- indexService.deleteKey(index)
      // _ <- queryCacheService.deleteAllCachesForKey(orgId, key)
      _ <- fileService.deleteRecursively(index.collectionsDirectory)
      _ <- fileService.deleteRecursively(index.analysisDirectory)
      // no need to delete compile directory as it's deleted after compile
      _ <- socketService.indexDeleted(orgId, repoId, indexId)
    } yield {
      ()
    }
  }
}
