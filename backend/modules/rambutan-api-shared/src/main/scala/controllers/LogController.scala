package controllers

import models._
import javax.inject._
import silvousplay.api.API
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.collection.immutable.Seq
import play.api.libs.json._

@Singleton
class LogController @Inject() (
  configuration: play.api.Configuration,
  authService:   services.AuthService,
  repoService:   services.RepoService,
  logService:    services.LogService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def listWork(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.listWorkRecords(orgId) map (_.map(_.dto))
      }
    }
  }

  def getWorkSummary(orgId: Int, status: Option[WorkStatus]) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.listWorkRecords(orgId) map { items =>
          val collections = status match {
            case Some(sfilter) => items.filter(_.self.status =?= sfilter)
            case _             => items
          }

          WorkSummary(
            items.length,
            collections).dto
        }
      }
    }
  }

  def getWorkTree(orgId: Int, workId: String) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.getWorkTree(orgId, workId) map (_.map(_.dto))
      }
    }
  }

  def getLogs(orgId: Int, workId: String, parentOnly: Option[Boolean], errorOnly: Option[Boolean]) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.getLogs(
          orgId,
          workId,
          parentOnly.getOrElse(false),
          errorOnly.getOrElse(false))
      }
    }
  }

  def getLogItem(orgId: Int, workId: String, logId: String) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.getLogItem(logId)
      }
    }
  }

  def cleanWork(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        repoService.cleanWork(orgId)
      }
    }
  }

  def deleteWork(orgId: Int, workId: String) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.deleteWork(orgId, workId)
      }
    }
  }

  def cycleLogIndex(orgId: Int) = {
    api { implicit request =>
      // super user?
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.cycleLogIndex()
      }
    }
  }

  def getLogIndex(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        logService.getLogIndex()
      }
    }
  }
}