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
class SavedTargetingController @Inject() (
  configuration:             play.api.Configuration,
  authService:               services.AuthService,
  savedTargetingDataService: services.SavedTargetingDataService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def createSavedTargeting(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        withJson { form: SavedTargetingForm =>
          savedTargetingDataService.createSavedTargeting(
            orgId,
            form.targetingName,
            form.targeting.map(_.repoId),
            form.targeting.flatMap(_.branch),
            form.targeting.flatMap(_.sha),
            form.fileFilter) map (_ => ())
        }
      }
    }
  }

  def listSavedTargeting(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        savedTargetingDataService.listSavedTargeting(orgId) map (_.map(_.dto))
      }
    }
  }
}
