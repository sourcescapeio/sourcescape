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
class OrganizationDataService @Inject() (
  dao:           dal.LocalDataAccessLayer,
  configuration: play.api.Configuration)(implicit actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) {

  val PreviewOrg = Organization(-1, 0, "Preview")

  def createOrganization(userId: Int, name: String): Future[Organization] = {
    val obj = Organization(0, userId, name)
    dao.OrganizationTable.insert(obj) map { id =>
      obj.copy(id = id)
    }
  }

  def getOrganizations(): Future[List[Organization]] = {
    dao.OrganizationTable.all
  }
}
