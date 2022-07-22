package services

import models.IndexType
import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._

@Singleton
class QueryTargetingService @Inject() (
  repoIndexDataService: RepoIndexDataService,
  configuration:        play.api.Configuration)(implicit ec: ExecutionContext) {

  def resolveTargeting(orgId: Int, indexType: IndexType, targetingRequest: QueryTargetingRequest): Future[KeysQueryTargeting] = {
    // deal with different types
    for {
      indexes <- targetingRequest match {
        case QueryTargetingRequest.ForIndexes(ids, _)     => repoIndexDataService.getIndexIds(ids)
        case QueryTargetingRequest.RepoLatest(repoIds, _) => repoIndexDataService.getLatestSHAIndexForRepos(repoIds).map(_.values.toList)
        case QueryTargetingRequest.AllLatest(files)       => repoIndexDataService.getAllSHAIndexesLatest(orgId)
      }
      diffIds = indexes.filter(_.isDiff).map(_.id)
      diffMap <- repoIndexDataService.getSHAIndexTreeBatch(diffIds)
      // TODO: this is a bit gross
      maybeFiles = targetingRequest match {
        case QueryTargetingRequest.ForIndexes(_, Some(filter)) => Option(filter.split(",").toList)
        case QueryTargetingRequest.RepoLatest(_, Some(filter)) => Option(filter.split(",").toList)
        case QueryTargetingRequest.AllLatest(Some(filter)) => Option(filter.split(",").toList)
        case _ => None
      }
    } yield {
      KeysQueryTargeting(indexType, indexes, diffMap, maybeFiles)
    }
  }
}
