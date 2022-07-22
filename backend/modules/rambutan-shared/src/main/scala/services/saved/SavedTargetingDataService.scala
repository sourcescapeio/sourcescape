package services

import models._
import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import java.util.Base64
import org.joda.time._

@Singleton
class SavedTargetingDataService @Inject() (
  dao:           dal.SharedDataAccessLayer,
  configuration: play.api.Configuration)(implicit ec: ExecutionContext) {

  def createSavedTargeting(orgId: Int, name: String, repoId: Option[Int], branch: Option[String], sha: Option[String], fileFilter: Option[String]): Future[SavedTargeting] = {
    val obj = SavedTargeting(0, orgId, name, repoId, branch, sha, fileFilter)

    for {
      id <- dao.SavedTargetingTable.insert(obj)
    } yield {
      obj.copy(id = id)
    }
  }

  def listSavedTargeting(orgId: Int): Future[List[SavedTargeting]] = {
    dao.SavedTargetingTable.byOrg.lookup(orgId).map(_.toList)
  }
}
