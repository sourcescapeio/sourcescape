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
class SavedQueryDataService @Inject() (
  dao:           dal.SharedDataAccessLayer,
  configuration: play.api.Configuration,
  logService:    LogService)(implicit ec: ExecutionContext) {

  def createSavedQuery(orgId: Int, name: String, context: query.SrcLogCodeQuery, selected: String, temporary: Boolean): Future[SavedQuery] = {
    val selectedContext = context.withSelected(selected :: Nil) // does this do anything?

    val obj = SavedQuery(
      0,
      orgId,
      name,
      context.language,
      Json.toJson(selectedContext.sortedNodes),
      Json.toJson(selectedContext.sortedEdges),
      Json.toJson(selectedContext.aliases),
      selected,
      temporary,
      created = new DateTime().getMillis())

    for {
      queryId <- dao.SavedQueryTable.insert(obj)
    } yield {
      obj.copy(id = queryId)
    }
  }

  def deleteSavedQuery(orgId: Int, id: Int): Future[Unit] = {
    dao.SavedQueryTable.byId.delete(id) map (_ => ())
  }

  def listSavedQueries(orgId: Int): Future[List[SavedQuery]] = {
    dao.SavedQueryTable.byOrg.lookupNotTemporary(orgId).map(_.toList)
  }

  def getSavedQuery(orgId: Int, id: Int): Future[Option[SavedQuery]] = {
    dao.SavedQueryTable.byId.lookup(id)
  }

  def getSavedQueryByContext(orgId: Int, context: SrcLogCodeQuery): Future[List[SavedQuery]] = {
    val nodes = Json.toJson(context.sortedNodes)
    val edges = Json.toJson(context.sortedEdges)
    dao.SavedQueryTable.byOrgNodeEdge.lookup(orgId, nodes, edges)
  }

  // def markAsComplete(id: Int): Future[Unit] = {
  //   dao.SavedQueryTable.updateStatusById.update(id, SavedQueryStatus.Cached) map (_ => ())
  // }

}
