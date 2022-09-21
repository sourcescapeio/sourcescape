package controllers

import models._
import javax.inject._
import silvousplay.api._
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import play.api.libs.json._

@Singleton
class TargetingController @Inject() (
  configuration:    play.api.Configuration,
  telemetryService: TelemetryService,
  authService:      services.AuthService,
  targetingService: services.TargetingService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def checkTargeting(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        withJson { form: TargetingForm =>
          telemetryService.withTelemetry { implicit c =>
            form match {
              case TargetingForm(Some(repoId), None, None) => {
                targetingService.repoTargeting(orgId, repoId)
              }
              case TargetingForm(Some(repoId), Some(branch), None) => {
                targetingService.branchTargeting(orgId, repoId, branch)
              }
              case TargetingForm(Some(repoId), None, Some(sha)) => {
                targetingService.shaTargeting(orgId, repoId, sha)
              }
              case _ => {
                throw Errors.badRequest("targeting.invalid", "Invalid targeting")
              }
            }
          }
        }
      }
    }
  }
}
