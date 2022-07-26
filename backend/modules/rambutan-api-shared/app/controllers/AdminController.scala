package controllers

import javax.inject._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Source, Flow, Sink }
import silvousplay.imports._
import play.api.mvc.WebSocket
import play.api.libs.json._

@Singleton
class AdminController @Inject() (
  configuration: play.api.Configuration,
  socketService: services.SocketService,
  authService:   services.AuthService,
  indexService:  services.IndexService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) extends API {

  /**
   * Real-time updates
   */
  // def updateSocket() = {
  //   websocket { implicit request =>
  //     authService.orgsAuthenticatedFor { orgIds =>
  //       socketService.openSocket(orgIds)
  //     }
  //   }
  // }

  def indexSummary() = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        indexService.getIndexSummary() map (_.map(_.dto))
      }
    }
  }
}
