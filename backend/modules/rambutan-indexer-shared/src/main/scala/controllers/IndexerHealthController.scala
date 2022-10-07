package controllers

import javax.inject._
import silvousplay.api.API
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.collection.immutable.Seq
import play.api.libs.json._

@Singleton
class IndexerHealthController @Inject() (
  configuration:          play.api.Configuration,
  queueManagementService: services.QueueManagementService,
  indexService:           services.IndexService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def listQueues() = {
    api { implicit request =>
      queueManagementService.listQueues()
    }
  }

  def health() = {
    api { implicit request =>
      "Healthy"
    }
  }
}
