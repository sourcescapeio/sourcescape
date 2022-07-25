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
class UserController @Inject() (
  configuration:               play.api.Configuration,
  organizationSettingsService: services.OrganizationSettingsService,
  organizationDataService:     services.OrganizationDataService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  // orgs
  def createOrg() = {
    api { implicit request =>
      withForm(CreateOrgForm.form) { form =>
        organizationDataService.createOrganization(-1, form.name) map (_ => ())
      }
    }
  }

  def getNuxState(orgId: Int) = {
    api { implicit request =>
      organizationSettingsService.getNuxState(orgId) map { completed =>
        Json.obj("completed" -> completed)
      }
    }
  }

  def setNuxCompleted(orgId: Int) = {
    api { implicit request =>
      organizationSettingsService.setNuxCompleted(orgId)
    }
  }

  // user
  val DefaultOrg = Organization(-1, 0, "Default")

  // TODO: get rid of dependency on user
  def getUserProfile() = {
    api { implicit request =>
      for {
        orgs <- organizationDataService.getOrganizations()
      } yield {
        UserProfile(
          "user@local",
          superUser = true,
          (DefaultOrg :: orgs).map(_.withRole(isAdmin = true))).dto
      }
    }
  }

}
