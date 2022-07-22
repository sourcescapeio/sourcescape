package services

import models._
import models.query._
import silvousplay.imports._
import javax.inject._
import play.api.libs.json._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import akka.stream.scaladsl.{ Source, Sink }
import org.joda.time._

case class QueryPlanChunk(keys: List[String], cache: QueryCache, cursor: QueryCacheCursor, offset: Option[Int], limit: Option[Int]) {

  def dto = QueryPlanChunkDTO(keys, cursor.dto, offset, limit)

  def scroll = QueryScroll(Some(cursor.cursorModel))

  def targetingRequest = {
    val indexIds = keys.flatMap(_.split("/").lastOption).map(_.toInt)
    QueryTargetingRequest.ForIndexes(indexIds, None)
  }
}

case class QueryPlanChunkDTO(targeting: List[String], cursor: QueryCacheCursorDTO, offset: Option[Int], limit: Option[Int])

object QueryPlanChunkDTO {
  implicit val writes = Json.writes[QueryPlanChunkDTO]
}

case class CachedQueryResult(
  sizeEstimate:   Long,
  progressSource: Source[Long, Any],
  columns:        List[QueryColumnDefinition],
  source:         Source[Map[String, JsValue], Any]) {
  def header = QueryResultHeader(isDiff = false, columns, sizeEstimate)

}

case class CacheResponse(
  queryCache:     QueryCache,
  existingKeys:   List[QueryCacheKey],
  missingIndexes: List[RepoSHAIndex]) {
}

class QueryCacheService @Inject() (
  configuration:          play.api.Configuration,
  dao:                    dal.SharedDataAccessLayer,
  queryTargetingService:  QueryTargetingService,
  logService:             LogService,
  cachingQueueService:    CachingQueueService,
  repoIndexDataService:   RepoIndexDataService,
  savedQueryDataService:  SavedQueryDataService,
  srcLogCompilerService:  SrcLogCompilerService,
  relationalQueryService: RelationalQueryService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  /**
   * Create
   */
  def createCache(orgId: Int, context: SrcLogCodeQuery, selected: String, queryId: Option[Int], indexIds: List[Int], fileFilter: Option[String]): Future[(CacheResponse, Option[String])] = {
    for {
      query <- queryId match {
        case Some(qid) => {
          savedQueryDataService.getSavedQuery(orgId, qid).map {
            _.getOrElse(throw models.Errors.notFound("query.dne", s"query not found ${qid}"))
          }
        }
        case None => {
          for {
            contextMatch <- savedQueryDataService.getSavedQueryByContext(orgId, context)
            selectedQuery = contextMatch.sortBy(_.temporary).headOption // false first
            savedQuery <- {
              selectedQuery match {
                case Some(s) => {
                  Future.successful(s)
                }
                case None => {
                  savedQueryDataService.createSavedQuery(orgId, Hashing.uuid(), context, selected, temporary = true)
                }
              }
            }
          } yield {
            savedQuery
          }
        }
      }
      // TODO: verify query
      // _ = withDefined(queryId) { _ =>
      //   query.nodes
      // }
      // select query cache
      record <- logService.createParent(orgId, Json.obj("task" -> "cache"))
      cacheResponse <- selectBestQueryCache(query, indexIds, fileFilter)
      _ = println(
        "Selected",
        cacheResponse)
      // maybe do the caching
      maybeItem <- ifNonEmpty(cacheResponse.missingIndexes) {
        val item = CachingQueueItem(
          orgId,
          cacheResponse.queryCache.id,
          record.id,
          cacheResponse.existingKeys.map(_.key),
          cacheResponse.missingIndexes.map(_.id))
        cachingQueueService.enqueue(item).map(_ => Option(item))
      }
    } yield {
      (cacheResponse, maybeItem.map(_.workId))
    }
  }

  private def selectBestQueryCache(query: SavedQuery, requestedIndexIds: List[Int], fileFilter: Option[String]): Future[CacheResponse] = {
    val orgId = query.orgId
    val selectedQuery = query.selectedQuery
    for {
      targeting <- queryTargetingService.resolveTargeting(orgId, query.language, QueryTargetingRequest.ForIndexes(requestedIndexIds, fileFilter))
      requestedQuery <- srcLogCompilerService.compileQuery(selectedQuery)(targeting)
      ordering = requestedQuery.calculatedOrdering
      root = requestedQuery.root.key
      //
      existing <- getCacheByQuery(orgId, query.id)
      // NOTE: what if multiple? << that's kind of an error condition
      selected = existing.filter { e =>
        val fileFilterMatch = e.fileFilter =?= fileFilter
        val rootMatch = e.forceRoot =?= root
        val orderingMatch = e.ordering =?= ordering
        fileFilterMatch && rootMatch && orderingMatch
      }.headOption
      cache <- selected match {
        case Some(c) => Future.successful((c))
        case _       => writeCache(QueryCache(0, orgId, query.id, root, ordering, fileFilter))
      }
      // calculate keys
      requestedIndexes <- repoIndexDataService.getIndexIds(requestedIndexIds)
      requestedSet = requestedIndexes.map(_.esKey).toSet
      existingCacheKeys <- getCacheKeys(cache.id).map {
        _.filter(i => requestedSet.contains(i.key))
      }
      existingSet = existingCacheKeys.map(_.key).toSet
      missingIndexes = requestedIndexes.filterNot { i =>
        existingSet.contains(i.esKey)
      }
    } yield {
      CacheResponse(
        cache,
        existingCacheKeys,
        missingIndexes)
    }
  }

  /**
   * Cache
   */
  def getCacheSummary(orgId: Int, cacheId: Int, keys: List[String]): Future[(Option[QueryCache], Int)] = {
    for {
      maybeCache <- getCache(cacheId)
      cacheKeys <- getCacheKeys(cacheId)
      filteredKeys = cacheKeys.filter { cacheKey =>
        keys.contains(cacheKey.key)
      } // filter
      total = filteredKeys.map(_.count).sum
    } yield {
      (maybeCache, total)
    }
  }

  def getCache(cacheId: Int): Future[Option[QueryCache]] = {
    dao.QueryCacheTable.byId.lookup(cacheId)
  }

  def getCacheByQuery(orgId: Int, queryId: Int): Future[List[QueryCache]] = {
    dao.QueryCacheTable.byOrgQuery.lookup((orgId, queryId))
  }

  private def writeCache(obj: QueryCache) = {
    for {
      cacheId <- dao.QueryCacheTable.insert(obj)
    } yield {
      obj.copy(id = cacheId)
    }
  }

  /**
   * Keys
   */
  def getCacheKeys(cacheId: Int): Future[List[QueryCacheKey]] = {
    dao.QueryCacheKeyTable.byCache.lookup(cacheId).map(_.filterNot(_.deleted))
  }

  def getExpired(cutoff: FiniteDuration): Source[QueryCacheKey, Any] = {
    val cutoffMillis = new DateTime().getMillis() - cutoff.toMillis
    dao.QueryCacheKeyTable.Streams.lastModifiedLessThan(cutoffMillis)
  }

  def getDeletedCacheKeys(): Source[QueryCacheKey, Any] = {
    dao.QueryCacheKeyTable.Streams.deleted
  }

  def writeCacheKeys(objs: List[QueryCacheKey]): Future[Unit] = {
    dao.QueryCacheKeyTable.insertBulk(objs)
  }

  def writeKey(key: QueryCacheKey): Future[Unit] = {
    dao.QueryCacheKeyTable.insertOrUpdate(key) map (_ => ())
  }

  def updateLastModified(cacheId: Int, keys: List[String]) = {
    val flattened = keys.map(cacheId -> _)
    dao.QueryCacheKeyTable.updateLastModifiedByCacheKey.updateBatch(flattened, new DateTime().getMillis())
  }

  def softDeleteCacheKeys(objs: List[QueryCacheKey]): Future[Unit] = {
    // mark as deleted for later stuff
    dao.QueryCacheKeyTable.updateDeletedByCacheKey.updateBatch(objs.map(_.pk), true) map (_ => ())
  }

  def hardDeleteCacheKeys(objs: List[QueryCacheKey]): Future[Unit] = {
    val pks = objs.map(_.pk)
    for {
      _ <- dao.QueryCacheCursorTable.byCacheKey.deleteBatch(pks)
      _ <- dao.QueryCacheKeyTable.byCacheKey.deleteBatch(pks)
    } yield {
      ()
    }
  }

  /**
   * Cursor
   */
  def writeCursors(cursors: List[QueryCacheCursor]): Future[Unit] = {
    dao.QueryCacheCursorTable.insertBulk(cursors) map (_ => ())
  }

  /**
   * Legacy ass shit. Deprecate
   */

  /**
   * Delete
   */
  def deleteAllCachesForKey(orgId: Int, key: String): Future[Unit] = {
    // for {
    //   _ <- dao.QueryCacheCursorTable.byKey.delete(key)
    //   _ <- dao.QueryCacheKeyTable.byKey.delete(key)
    // } yield {
    //   ()
    // }
    Future.successful(())
  }

  def deleteCacheForQuery(orgId: Int, queryId: Int): Future[Unit] = {
    // for {
    //   caches <- dao.QueryCacheTable.byOrgQuery.lookup(orgId, queryId)
    //   _ <- Source(caches).mapAsync(4) { cache =>
    //     deleteCache(orgId, cache.id)
    //   }.runWith(Sink.ignore)
    // } yield {
    //   ()
    // }
    Future.successful(())
  }

  def deleteCacheForQueryKey(orgId: Int, queryId: Int, key: String): Future[Unit] = {
    // for {
    //   caches <- dao.QueryCacheTable.byOrgQuery.lookup(orgId, queryId)
    //   cacheIds = caches.map(_.id -> key)
    //   _ <- dao.QueryCacheCursorTable.byCacheKey.deleteBatch(cacheIds)
    //   _ <- dao.QueryCacheKeyTable.byCacheKey.deleteBatch(cacheIds)
    // } yield {
    //   ()
    // }
    Future.successful(())
  }

  def deleteCache(orgId: Int, cacheId: Int): Future[Unit] = {
    // for {
    //   _ <- dao.QueryCacheCursorTable.byCache.delete(cacheId)
    //   _ <- dao.QueryCacheKeyTable.byCache.delete(cacheId)
    //   _ <- dao.QueryCacheTable.byId.delete(cacheId)
    // } yield {
    //   ()
    // }
    Future.successful(())
  }

  /**
   * Cache info
   */
  def hydrateCacheInfo(orgId: Int, queries: List[SavedQuery]): Future[List[SavedQueryCacheDetails]] = {
    for {
      caches <- dao.QueryCacheTable.byOrgQuery.lookupBatch(queries.map(orgId -> _.id))
      cacheMap = caches.map {
        case ((_, k), v) => k -> v
      }
      cacheIds = caches.values.toList.flatten.map(_.id).distinct
      allKeys <- dao.QueryCacheKeyTable.byCache.lookupBatch(cacheIds)
      allCursors <- dao.QueryCacheCursorTable.byCache.lookupBatch(cacheIds) // remap
    } yield {
      queries.map { query =>
        val queryCaches = cacheMap.getOrElse(query.id, Nil)
        val cacheKeys = queryCaches.flatMap(cache => allKeys.getOrElse(cache.id, Nil).map(cache -> _))
        val cursorMap = queryCaches.flatMap(cache => allCursors.getOrElse(cache.id, Nil)).groupBy(_.key)

        val inner = cacheKeys.map {
          case (cache, key) => {
            val cursors = cursorMap.getOrElse(key.key, Nil)

            SavedQueryKeyDetails(key, cache, cursors)
          }
        }

        SavedQueryCacheDetails(
          query,
          inner)
      }
    }
  }

  /**
   * Deprecate
   */
  // def getCursors(orgId: Int, queryId: Int): Future[List[QueryCacheCursor]] = {
  //   for {
  //     caches <- dao.QueryCacheTable.byOrgQuery.lookup(orgId, queryId)
  //     allKeys <- dao.QueryCacheCursorTable.byCache.lookupBatch(caches.map(_.id))
  //   } yield {
  //     allKeys.values.flatten.toList
  //   }
  // }

  // def getKeys(orgId: Int, queryId: Int): Future[List[QueryCacheKey]] = {
  //   for {
  //     caches <- dao.QueryCacheTable.byOrgQuery.lookup(orgId, queryId)
  //     allKeys <- dao.QueryCacheKeyTable.byCache.lookupBatch(caches.map(_.id))
  //   } yield {
  //     allKeys.values.flatten.toList
  //   }
  // }

  /**
   * Use
   */
  private def getMaxCursor(cacheId: Int, key: String, index: Int): Future[Option[QueryCacheCursor]] = {
    // get max
    dao.QueryCacheCursorTable.byCacheKey.maxKey(cacheId, key, index)
  }

  def getQueryPlan(orgId: Int, cacheId: Int, start: Int, end: Int, keys: List[String]): Future[Option[QueryPlanChunk]] = {
    for {
      cache <- getCache(cacheId).map {
        _.getOrElse(throw models.Errors.notFound("cache.dne", "Cache not found"))
      }
      cacheKeys <- getCacheKeys(cacheId).map {
        _.filter { k =>
          val inTargeting = keys.contains(k.key)
          val hasCount = k.count > 0
          inTargeting && hasCount
        }.sortBy(_.key)
      }
      // remap count into idx
      (_, indexMap) = cacheKeys.foldLeft((0, List.empty[(String, (Int, Int))])) {
        case ((acc, listAcc), cacheKey) => {
          val nextCount = acc + cacheKey.count
          val addTup = cacheKey.key -> (acc, nextCount - 1)

          (nextCount, listAcc :+ addTup)
        }
      }
      filteredIndexes = indexMap.filterNot {
        case (_, (_, endIdx)) if endIdx < start   => true
        case (_, (startIdx, _)) if startIdx > end => true
        case _                                    => false
      }
      maybeCursor <- withDefined(filteredIndexes.headOption) {
        case (k, (startIdx, _)) => {
          val normalizedStart = start - startIdx // startIdx is always < start
          // normalize start
          for {
            maxCursor <- getMaxCursor(cacheId, k, normalizedStart)
          } yield {
            // println(dropLeft + , maxCursor)
            maxCursor.map { curs =>
              ((normalizedStart - curs.start) -> curs)
            }
          }
        }
      }
      res = withDefined(maybeCursor) {
        case (offset, cursor) => Option {
          QueryPlanChunk(
            keys = filteredIndexes.map(_._1),
            cache = cache,
            cursor = cursor,
            offset = Some(offset),
            limit = Some(end - start + 1))
        }
      }
    } yield {
      res
    }
  }

  def executeQueryPlan(queryPlan: QueryPlanChunk) = {
    val cache = queryPlan.cache
    val orgId = cache.orgId
    val queryId = cache.queryId

    for {
      savedQuery <- savedQueryDataService.getSavedQuery(orgId, queryId).map {
        _.getOrElse(throw new Exception("invalid query"))
      }
      targeting <- queryTargetingService.resolveTargeting(orgId, savedQuery.language, queryPlan.targetingRequest)
      selectedQuery = savedQuery.selectedQuery.copy(root = Some(cache.forceRoot))
      compiledQuery <- srcLogCompilerService.compileQuery(selectedQuery)(targeting)
      adjustedQuery = compiledQuery.copy(
        offset = queryPlan.offset,
        limit = queryPlan.limit,
        forceOrdering = Some(cache.ordering))
      // consume as one big chunk?
      res <- relationalQueryService.runQuery(
        adjustedQuery,
        explain = false,
        progressUpdates = true)(targeting, queryPlan.scroll)
    } yield {
      CachedQueryResult(res.sizeEstimate, res.progressSource, res.columns, res.source)
    }
  }
}
