package services

import models._
import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow, Sink, Keep, GraphDSL, Merge, Broadcast }
import akka.stream.OverflowStrategy
import org.joda.time.DateTime

@Singleton
class CachingService @Inject() (
  configuration:          play.api.Configuration,
  queueManagementService: QueueManagementService,
  logService:             LogService,
  socketService:          SocketService,
  queryTargetingService:  QueryTargetingService,
  cachingQueueService:    CachingQueueService,
  queryCacheService:      QueryCacheService,
  savedQueryDataService:  SavedQueryDataService,
  srcLogCompilerService:  SrcLogCompilerService,
  relationalQueryService: RelationalQueryService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val CachingConcurrency = 4

  def startCaching() = {
    // shutdown everything
    for {
      // get repos
      _ <- cachingQueueService.clearQueue()
      _ <- queueManagementService.runQueue[CachingQueueItem](
        "cache-data",
        concurrency = CachingConcurrency,
        source = cachingQueueService.source) { item =>
          println("DEQUEUE", item)
          runCaching(item)
        } { item =>
          println("COMPLETE", item)
          Future.successful(())
        }
    } yield {
      ()
    }
  }

  private def runCaching(item: CachingQueueItem) = {
    val orgId = item.orgId
    val cacheId = item.cacheId
    for {
      record <- logService.getRecord(item.workId).map(_.getOrElse(throw new Exception("record not found")))
      cache <- queryCacheService.getCache(cacheId).map {
        _.getOrElse(throw new Exception(s"Cache ${cacheId} does not exist"))
      }
      queryId = cache.queryId
      query <- savedQueryDataService.getSavedQuery(orgId, queryId).map {
        _.getOrElse(throw new Exception(s"Query ${queryId} not found"))
      }
      _ <- updateCache(orgId, cache, query, item.indexIds, item.existingKeys)(record)
    } yield {
      ()
    }
  }

  val MaxCursorGap = 100
  private def updateCache(orgId: Int, cache: QueryCache, query: SavedQuery, indexIds: List[Int], existingKeys: List[String])(record: WorkRecord): Future[Unit] = {
    val cacheId = cache.id
    for {
      // getKeysForIndex(indexIds)
      // existingKeys()
      _ <- logService.startRecord(record)
      root = cache.forceRoot
      _ <- socketService.cachingProgress(orgId, record.id, 0)
      targeting <- queryTargetingService.resolveTargeting(orgId, query.language, QueryTargetingRequest.ForIndexes(indexIds, cache.fileFilter))
      selectedQuery = query.selectedQuery.copy(root = Some(root))
      relationalQuery <- srcLogCompilerService.compileQuery(selectedQuery)(targeting)
      adjustedQuery = relationalQuery.copy(forceOrdering = Some(cache.ordering), limit = None)
      // run query internal
      _ = println(targeting)
      queryScroll = QueryScroll(None)
      tracing = QueryTracing.Basic
      (size, _, progressSource, source) <- relationalQueryService.runQueryInternal(
        adjustedQuery,
        shouldExplain = false,
        progressUpdates = false)(targeting, tracing, queryScroll)
      // we pull these to insert into our progress updates
      // TODO: not dealing with this now
      // existingKeyObjs <- queryCacheService.getCacheKeys(cacheId).map {
      //   _.filter(i => existingKeys.contains(i.key))
      // }
      // remap roots to diff keys.
      // assumes we only have one key per repo
      remapIds = targeting.diffKeys.map { k =>
        k.rootKey -> k.diffKey
      }.toMap
      // assume ordered
      // also assume small number of keys
      keyMap <- source.groupedWithin(MaxCursorGap, 100.milliseconds).map {
        case chunks => {
          // group by root key
          val grouped = chunks.toList.groupBy { traceMap =>
            traceMap.getOrElse(root, throw new Exception("invalid trace")).terminusId.key match {
              case k if remapIds.isDefinedAt(k) => remapIds.get(k).get
              case k                            => k
            }
          }

          // spit out cursor at beginning
          grouped.flatMap {
            case (k, vs @ v :: _) => {
              val cursor = RelationalKey(
                v.map {
                  case (k, v) => k -> targeting.relationalKeyItem(v.terminusId)
                })
              Some(k -> (vs.length, cursor))
            }
            case _ => None
          }
        }
      }.scanAsync(Map.empty[String, Int]) {
        case (prevMap, nextItems) => {
          val calcMap = nextItems.map {
            case (k, (l, cursor)) => {
              val prevCount = prevMap.getOrElse(k, 0)
              val nextCount = prevCount + l
              val cursorObj = QueryCacheCursor(cacheId, k, prevCount, nextCount - 1, Json.toJson(cursor))

              k -> (nextCount, cursorObj)
            }
          }

          val cacheCursors = calcMap.map {
            case (_, (_, c)) => c
          }.toList

          for {
            _ <- queryCacheService.writeCursors(cacheCursors)
            emit = prevMap ++ calcMap.map {
              case (k, (c, _)) => k -> c
            }
            // make progress calculation
            // NOTE: this does not take into account existing cache keys so will
            // - always be an underestimate until we pop in at the end
            available = emit.values.sum
            _ <- socketService.cachingAvailable(orgId, record.id, available)
          } yield {
            emit
          }
        }
      }.runWith(Sink.last) // Sink.last then calculate
      //
      resultKeys = keyMap.map {
        case (k, count) => QueryCacheKey(cacheId, k, count, deleted = false, lastModified = new DateTime().getMillis())
      }.toList
      remainder = targeting.allKeys.toSet.diff(keyMap.keySet).toList.map { key =>
        QueryCacheKey(cacheId, key, 0, deleted = false, lastModified = new DateTime().getMillis())
      }
      _ <- queryCacheService.writeCacheKeys(resultKeys ++ remainder)
      _ <- logService.finishRecord(record)
      _ <- socketService.cachingFinished(orgId, record.id)
    } yield {
      ()
    }
  }
}
