package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import java.util.Base64
import akka.stream.scaladsl.Source

@Singleton
class OrganizationSettingsService @Inject() (
  localDao:      dal.LocalDataAccessLayer,
  configuration: play.api.Configuration)(implicit ec: ExecutionContext) {

  def getNuxState(orgId: Int): Future[Boolean] = {
    localDao.LocalOrgSettingsTable.byOrg.lookup(orgId) map (_.map(_.completedNux).getOrElse(false))
  }

  def setNuxCompleted(orgId: Int): Future[Unit] = {
    localDao.LocalOrgSettingsTable.insertOrUpdate(LocalOrgSettings(orgId, true)) map (_ => ())
  }
}
