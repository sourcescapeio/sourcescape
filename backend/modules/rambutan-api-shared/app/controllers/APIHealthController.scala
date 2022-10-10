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
import play.twirl.api.HtmlFormat
import silvousplay.data.health.{ CreateTable, DatabaseDiff }

abstract class APIHealthController @Inject() (
  indexService: services.IndexService,
  authService:  services.AuthService)(implicit ec: ExecutionContext) extends API {

  val all: List[CanInitializeTable]

  def checkDatabaseDiff(items: List[CanInitializeTable]): Future[DatabaseDiff]

  def healthView(diff: DatabaseDiff): HtmlFormat.Appendable

  def diff() = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        for {
          diff <- checkDatabaseDiff(all)
        } yield {
          healthView(diff)
        }
      }
    }
  }

  import silvousplay.data.health.{ CreateTable, DropTable, DatabaseDiff }
  def create() = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        val creates = (all).map { t =>
          CreateTable(t.tableName, t.createStatements.toList)
        }

        Future.successful {
          healthView(DatabaseDiff(creates, Nil, Nil))
        }
      }
    }
  }

  def deletes() = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        val drops = (all).map { t =>
          CreateTable(t.tableName, t.dropStatements.toList)
        }

        Future.successful {
          healthView(DatabaseDiff(drops, Nil, Nil))
        }
      }
    }
  }

  def cycleIndexes(indexType: models.IndexType) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        for {
          _ <- indexService.cycleEdgesIndex(indexType)
          _ <- indexService.cycleNodesIndex(indexType)
        } yield {
          ()
        }
      }
    }
  }

  def health() = {
    api { implicit request =>
      "Healthy"
    }
  }
}
