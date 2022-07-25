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

@Singleton
class SavedQueryController @Inject() (
  configuration:         play.api.Configuration,
  authService:           services.AuthService,
  savedQueryService:     services.SavedQueryService,
  savedQueryDataService: services.SavedQueryDataService,
  queryCacheService:     services.QueryCacheService,
  queryTargetingService: services.QueryTargetingService,
  cachingQueueService:   services.CachingQueueService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def createSavedQuery(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      withJson { form: SavedQueryForm =>
        authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
          savedQueryDataService.createSavedQuery(
            orgId,
            form.queryName,
            form.context.toModel,
            form.selected,
            temporary = false) map (_.dto)
        }
      }
    }
  }

  def listSavedQueries(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        savedQueryDataService.listSavedQueries(orgId) map (_.map(_.dto))
      }
    }
  }

  def getSavedQuery(orgId: Int, id: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        savedQueryService.getSavedQuery(orgId, id) map (_.map(_.dto))
      }
    }
  }

  def deleteSavedQuery(orgId: Int, id: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        savedQueryService.deleteSavedQuery(orgId, id)
      }
    }
  }

  /**
   *
   * destroy
   */
  // def deleteCache(orgId: Int, queryId: Int, key: Option[String]) = {
  //   api { implicit request =>
  //     authenticationService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
  //       key match {
  //         case Some(k) => queryCacheService.deleteCacheForQueryKey(orgId, queryId, k)
  //         case _       => queryCacheService.deleteCacheForQuery(orgId, queryId)
  //       }
  //     }
  //   }
  // }

  // def regenerateCache(orgId: Int, queryId: Int) = {
  //   api { implicit request =>
  //     for {
  //       // targeting <- queryTargetingService.resolveTargeting(orgId, QueryTargetingRequest.AllLatest(None))
  //       // run count
  //       _ <- Future.successful(())
  //       // _ <- cachingQueueService.enqueueForQuery(orgId, queryId)
  //       // _ <- queryCacheService.deleteCacheForQuery(orgId, queryId)
  //       // _ <- queryCacheService.initializeCache(orgId, queryId)(targeting)
  //       // count <- queryCacheService.getQueryCount(orgId, savedQuery)(targeting)
  //       // results <- queryCacheService.getQueryResults(orgId, savedQuery)(targeting)
  //     } yield {
  //       ()
  //     }
  //   }
  // }
}
