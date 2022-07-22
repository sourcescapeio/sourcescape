package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.libs.json._
import org.joda.time._

@Singleton
class SavedQueryService @Inject() (
  dao:                   dal.SharedDataAccessLayer,
  configuration:         play.api.Configuration,
  savedQueryDataService: SavedQueryDataService,
  queryCacheService:     QueryCacheService,
  logService:            LogService)(implicit ec: ExecutionContext) {

  def deleteSavedQuery(orgId: Int, id: Int): Future[Unit] = {
    for {
      _ <- queryCacheService.deleteCacheForQuery(orgId, id)
      _ <- savedQueryDataService.deleteSavedQuery(orgId, id)
    } yield {
      ()
    }
  }

  def getSavedQuery(orgId: Int, id: Int): Future[Option[SavedQuery]] = {
    savedQueryDataService.getSavedQuery(orgId, id)
  }
}
