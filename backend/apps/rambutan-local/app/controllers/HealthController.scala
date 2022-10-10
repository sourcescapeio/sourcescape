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
import silvousplay.data.CanInitializeTable
import silvousplay.data.health.{ CreateTable, DropTable, DatabaseDiff }

@Singleton
class HealthController @Inject() (
  authService:  services.AuthService,
  sharedDao:    dal.SharedDataAccessLayer,
  localDao:     dal.LocalDataAccessLayer,
  indexService: services.IndexService)(implicit ec: ExecutionContext, as: ActorSystem) extends APIHealthController(
  indexService,
  authService) {

  val all: List[CanInitializeTable] = localDao.all() ++ sharedDao.all()

  def checkDatabaseDiff(items: List[CanInitializeTable]): Future[DatabaseDiff] = {
    sharedDao.checkDatabaseDiff(items)
  }

  def healthView(diff: DatabaseDiff) = {
    views.html.dbhealth(diff)
  }
}
