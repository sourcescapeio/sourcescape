package controllers

import models._
import models.query._
import javax.inject._
import silvousplay.api.API
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Source, Sink, Merge }
import play.api.libs.json._
import akka.util.ByteString
import silvousplay.api._

@Singleton
class CacheController @Inject() (
  configuration:        play.api.Configuration,
  telemetryService:     TelemetryService,
  authService:          services.AuthService,
  savedQueryService:    services.SavedQueryService,
  queryCacheService:    services.QueryCacheService,
  cachingQueueService:  services.CachingQueueService,
  repoIndexDataService: services.RepoIndexDataService)(implicit ec: ExecutionContext, as: ActorSystem) extends API with StreamResults {

  def createCache(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedReposForOrg(orgId, RepoRole.Pull) { repos =>
        withJson { form: CacheForm =>
          val repoIds = repos.map(_.repoId)
          for {
            indexIds <- form.indexIds match {
              case Some(i) => repoIndexDataService.verifiedIndexIds(i, repoIds.toSet)
              case None    => repoIndexDataService.getLatestSHAIndexForRepos(repoIds).map(_.values.map(_.id).toList)
            }
            (cacheResponse, maybeWorkId) <- queryCacheService.createCache(
              orgId,
              form.context.toModel,
              form.selected,
              form.queryId,
              indexIds,
              form.fileFilter)
            existingKeys = cacheResponse.existingKeys.map(_.key)
            _ <- queryCacheService.updateLastModified(cacheResponse.queryCache.id, existingKeys)
            missingIndexes <- repoIndexDataService.getIndexIds(cacheResponse.missingIndexes.map(_.id))
            maybeCount = ifEmpty(missingIndexes) {
              Option(cacheResponse.existingKeys.map(_.count).sum)
            }
          } yield {
            // if missing keys is empty
            Json.obj(
              "cache" -> cacheResponse.queryCache.dto,
              "keys" -> (existingKeys ++ missingIndexes.map(_.esKey)),
              "count" -> maybeCount,
              "workId" -> maybeWorkId)
          }
        }
      }
    }
  }

  def getCache(orgId: Int, cacheId: Int, keys: List[String]) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        queryCacheService.getCacheSummary(orgId, cacheId, keys) map {
          case (Some(cache), count) => Option {
            Json.obj("cache" -> cache.dto, "keys" -> keys, "count" -> count)
          }
          case _ => None
        }
      }
    }
  }

  /**
   * Results
   */
  private def streamResult(result: services.CachedQueryResult) = {
    val tableHeader = result.header
    val source = result.source
    val progressSource = result.progressSource.map(Left.apply)
    val renderedSource = source.map(Right.apply)
    val mergedSource = renderedSource.merge(progressSource).map {
      case Left(progress) => {
        Json.obj(
          "type" -> "progress",
          "progress" -> progress)
      }
      case Right(dto) => {
        Json.obj(
          "type" -> "data",
          "obj" -> dto)
      }
    }

    streamQuery(tableHeader, mergedSource)
  }

  def getCacheData(orgId: Int, cacheId: Int, start: Int, end: Int, keys: List[String]) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        telemetryService.withTelemetry { implicit context =>
          for {
            queryPlan <- queryCacheService.getQueryPlan(orgId, cacheId, start, end, keys).map {
              _.getOrElse(throw models.Errors.badRequest("invalid.plan", "No query plan can be derived"))
            }
            _ <- queryCacheService.updateLastModified(cacheId, keys)
            result <- queryCacheService.executeQueryPlan(queryPlan)
          } yield {
            streamResult(result)
          }
        }
      }
    }
  }

  def getQueryPlan(orgId: Int, cacheId: Int, start: Int, end: Int, keys: List[String]) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        for {
          plan <- queryCacheService.getQueryPlan(orgId, cacheId, start, end, keys)
        } yield {
          plan.map(_.dto)
        }
      }
    }
  }
}
