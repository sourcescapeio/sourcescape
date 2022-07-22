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
class CacheSweeperService @Inject() (
  configuration:          play.api.Configuration,
  queryCacheService:      QueryCacheService,
  cronService:            CronService,
  queueManagementService: QueueManagementService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val CacheSweeperConcurrency = 1
  val CacheSweeperTick = 30.minutes
  val CacheDeletionTick = 30.minutes
  val CacheExpiration = 12.hours

  def startSweeper() = {
    queueManagementService.runQueue[List[QueryCacheKey]](
      "cache-sweeper-cron",
      concurrency = CacheSweeperConcurrency,
      source = cronService.sourceFlatMap(CacheSweeperTick) {
        println("RUNNING CACHE SWEEPER", new DateTime())
        // get expired keys
        queryCacheService.getExpired(CacheExpiration)
          .groupedWithin(100, 100.milliseconds)
          .map(_.toList)
      }) { items =>
        println(s"SWEEPING ${items.length} CACHES", items.map(_.pk))
        queryCacheService.softDeleteCacheKeys(items).map(_ => ())
      } { item =>
        // println("COMPLETE", item)
        Future.successful(())
      }
  }

  // this does actual deletion
  def startDeletion() = {
    queueManagementService.runQueue[List[QueryCacheKey]](
      "cache-deletion-cron",
      concurrency = CacheSweeperConcurrency,
      source = cronService.sourceFlatMap(CacheDeletionTick) {
        queryCacheService.getDeletedCacheKeys().groupedWithin(100, 100.milliseconds).map(_.toList)
      }) { items =>
        println(s"HARD DELETING ${items.length} CACHES", items.map(_.pk))
        queryCacheService.hardDeleteCacheKeys(items)
      } { item =>
        // println("COMPLETE", item)
        Future.successful(())
      }
  }
}
