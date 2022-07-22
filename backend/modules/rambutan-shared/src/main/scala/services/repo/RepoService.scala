package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import java.util.Base64
import akka.stream.scaladsl.{ Source, Sink }

@Singleton
class RepoService @Inject() (
  configuration:        play.api.Configuration,
  indexService:         IndexService,
  logService:           LogService,
  repoDataService:      RepoDataService,
  repoIndexDataService: RepoIndexDataService,
  queryCacheService:    QueryCacheService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  def getIndexTree(indexId: Int): Future[List[SHAIndexTreeListing]] = {
    for {
      index <- repoIndexDataService.getIndexId(indexId).map {
        _.getOrElse(throw models.Errors.notFound("index.dne", "Index not found"))
      }
      trees <- repoIndexDataService.getSHAIndexTreeBatch(indexId :: index.rootIndexId.toList).map(_.values.flatten)
    } yield {
      groupedDirectory(
        "",
        trees.toList.map(_.split("/").toList))
    }
  }

  private def groupedDirectory(
    currPath: String,
    in:       List[List[String]]): List[SHAIndexTreeListing] = {
    val recursedDirectories = {
      val directories: Map[String, List[List[String]]] = in.flatMap {
        case Nil => {
          throw new Exception("recursion error")
          None // error. should never get here
        }
        case a :: Nil => None
        case a :: b   => Some(a -> b)
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap

      directories.map {
        case (a, subs) =>
          val nextPath = s"${currPath}${a}/"
          SHAIndexTreeListing(
            a,
            nextPath,
            groupedDirectory(nextPath, subs))
      }.toList
    }

    val files = in.flatMap {
      case a :: Nil => Some {
        val path = s"${currPath}${a}"
        SHAIndexTreeListing(
          a,
          path,
          Nil)
      }
      case _ => None
    }

    recursedDirectories.sortBy(_.path) ++ files.sortBy(_.path)
  }

  def getBranchSummary(orgId: Int, repoId: Int, branch: String): Future[List[HydratedRepoSHA]] = {
    // returns branch summary
    for {
      branchChain <- repoIndexDataService.getBranchChain(orgId, repoId, branch)
      allIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, branchChain.map(_.sha).distinct).map {
        _.groupBy(_.sha)
      }
      latestIndexId <- repoIndexDataService.getLatestSHAIndex(repoId)
    } yield {
      branchChain.map { repoSHA =>
        val indexes = allIndexes.getOrElse(repoSHA.sha, Nil)

        HydratedRepoSHA(
          repoSHA.copy(refs = List(branch)),
          indexes,
          latestIndexId)
      }
    }
  }

  def hydrateRepoSummary(repos: List[RepoWithSettings]): Future[List[UnifiedRepoSummary]] = {

    val repoIds = repos.map(_.repo.repoId)
    for {
      indexMap <- repoIndexDataService.getIndexesForRepo(repoIds)
      latestMap <- repoIndexDataService.getLatestSHAIndexForRepos(repoIds).map {
        _.map {
          case (k, v) => k -> v.id
        }
      }
      //
      flattened = indexMap.values.flatten.toList
      latestIndexes = latestMap.map {
        case (k, v) => {
          k -> indexMap.getOrElse(k, Nil).find(_.id =?= v)
        }
      }
      shas = latestIndexes.flatMap {
        case (k, Some(i)) => Some((i.repoId, i.sha))
        case _            => None
      }.toList.distinct
      shaMap <- repoIndexDataService.getSHAsBatch(shas).map {
        _.values.flatten.map(i => i.repoId -> i).toMap
      }
      logs <- logService.getRecords(flattened.map(_.workId)).map {
        _.map(i => i.id -> i).toMap
      }
    } yield {
      repos.map { repo =>
        repo.unified(indexMap, latestIndexes, logs, shaMap)
      }.sortBy(_.repo)
    }
  }

  def cleanWork(orgId: Int): Future[Unit] = {
    for {
      works <- logService.listWorkRecords(orgId)
      indexes <- repoIndexDataService.getIndexesForOrg(orgId)
      // get indexes
      validWork = indexes.map(_.workId).toSet
      toDelete = works.filterNot(validWork contains _.self.id).map(_.self.id)
      _ <- Source(toDelete).mapAsync(4) { workId =>
        logService.deleteWork(orgId, workId)
      }.runWith(Sink.ignore)
    } yield {
      ()
    }
  }

  def deleteSHAIndex(orgId: Int, repoId: Int, indexId: Int): Future[Unit] = {
    repoIndexDataService.markIndexDeleted(indexId) map (_ => ())
  }
}
