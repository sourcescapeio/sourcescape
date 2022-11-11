package services

import models._
import models.query._
import models.graph._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.Source
import silvousplay.api.SpanContext

@Singleton
class RepoIndexDataService @Inject() (
  dao:                  dal.SharedDataAccessLayer,
  repoDataService:      RepoDataService,
  graphQueryService:    GraphQueryService,
  nodeHydrationService: NodeHydrationService,
  indexerService:       IndexerService,
  configuration:        play.api.Configuration)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  /**
   *
   * SHAs
   */
  def getBranchChain(orgId: Int, repoId: Int, branch: String, limit: Int = 20)(implicit context: SpanContext): Future[List[RepoSHA]] = {
    implicit val targeting = GenericGraphTargeting(orgId)

    for {
      (_, source) <- graphQueryService.runQueryGenericGraph(GraphQuery(
        GraphRoot(GenericGraphNodePredicate.GitHead.filters(
          Some(GraphPropertyCondition(
            List(GenericGraphProperty("repo_id", repoId.toString()), GenericGraphProperty("head", branch)))))),
        List(
          LinearTraverse(EdgeFollow(EdgeTypeTraverse(GenericGraphEdgeType.GitHeadCommit, None) :: Nil, FollowType.Target) :: Nil),
          RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
            EdgeFollow(
              EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent, filter = None) :: Nil,
              FollowType.Target),
            { trace =>
              val limitTerminate = (trace.tracesInternal.length + 1) >= limit
              limitTerminate
            }))))
      items <- source.runWith(Sinks.ListAccum)
      results <- getSHAs(repoId, items)
    } yield {
      results
    }
  }

  def getBelowChain(orgId: Int, repoId: Int, sha: String)(implicit context: SpanContext): Future[List[RepoSHA]] = {
    implicit val targeting = GenericGraphTargeting(orgId)

    val Limit = 100
    for {
      (_, source) <- graphQueryService.runQueryGenericGraph(GraphQuery(
        GraphRoot(GenericGraphNodePredicate.GitCommit.filters(
          Some(GraphPropertyCondition(
            List(GenericGraphProperty("repo_id", repoId.toString()), GenericGraphProperty("sha", sha)))))),
        RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
          EdgeFollow(
            EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent, filter = None) :: Nil,
            FollowType.Target),
          { trace =>
            val limitTerminate = (trace.tracesInternal.length + 1) >= Limit
            limitTerminate
          }) :: Nil))
      items <- source.runWith(Sinks.ListAccum)
      results <- getSHAs(repoId, items)
    } yield {
      results
    }
  }

  def getAboveChain(orgId: Int, repoId: Int, sha: String)(implicit context: SpanContext): Future[List[RepoSHA]] = {
    implicit val targeting = GenericGraphTargeting(orgId)

    val Limit = 100
    for {
      (_, source) <- graphQueryService.runQueryGenericGraph(GraphQuery(
        GraphRoot(GenericGraphNodePredicate.GitCommit.filters(
          Some(GraphPropertyCondition(
            List(GenericGraphProperty("repo_id", repoId.toString()), GenericGraphProperty("sha", sha)))))),
        RepeatedEdgeTraverse[GraphTrace[GenericGraphUnit], GenericGraphUnit](
          EdgeFollow(
            EdgeTypeTraverse(GenericGraphEdgeType.GitCommitParent.opposite, filter = None) :: Nil,
            FollowType.Target),
          { trace =>
            val limitTerminate = (trace.tracesInternal.length + 1) >= Limit
            limitTerminate
          }) :: Nil))
      items <- source.runWith(Sinks.ListAccum)
      results <- getSHAs(repoId, items)
    } yield {
      results
    }
  }

  private def getSHAs(repoId: Int, items: List[GraphTrace[GenericGraphNode]]) = {
    val shas = items.map { i =>
      i.terminusId
    }.flatMap {
      _.props.filter(_.key =?= "sha")
    }.map(_.value).distinct

    for {
      shaObjs <- getSHAsBatch(shas.map(repoId -> _)).map(_.values.flatten.toList)
    } yield {
      val shaMap = shaObjs.map { sha =>
        sha.sha -> sha
      }.toMap
      val graphIds = shaObjs.flatMap(sha => sha.parents.map(_ -> sha.sha))
      val sortedIds = silvousplay.TSort.topologicalSort(graphIds)
      sortedIds.flatMap(shaMap.get).toList.reverse
    }
  }

  // def getSHAsForOrg(orgId: Int): Future[List[RepoSHA]] = {
  //   for {
  //     repos <- repoDataService.getReposForOrg(orgId)
  //     shas <- getSHAsForRepos(repos.map(_.repoId))
  //   } yield {
  //     shas
  //   }
  // }

  // marked for deprecation
  def getSHAsForRepos(repoIds: List[Int]): Future[List[RepoSHA]] = {
    for {
      shas <- dao.RepoSHATable.byRepo.lookupBatch(repoIds)
    } yield {
      shas.values.flatten.toList
    }
  }

  private def getSHAsForRepoBranch(repoId: Int, branch: String): Future[List[RepoSHA]] = {
    dao.RepoSHATable.byRepoBranch.lookup(repoId, branch) map (_.toList)
  }

  // marked for deprecation
  def getSHA(repoId: Int, sha: String): Future[Option[RepoSHA]] = {
    dao.RepoSHATable.byPK.lookup((repoId, sha))
  }

  def getSHAsBatch(shas: List[(Int, String)]): Future[Map[(Int, String), Option[RepoSHA]]] = {
    dao.RepoSHATable.byPK.lookupBatch(shas)
  }

  def upsertSHAs(shas: List[RepoSHA]): Future[Unit] = {
    dao.RepoSHATable.insertOrUpdateBatch(shas) map (_ => ())
  }

  // def writeSHA(sha: RepoSHA) = {
  //   dao.RepoSHATable.insertOrUpdate(sha)
  // }

  /**
   * Indexes
   */
  // used for cleaning
  def getIndexesForOrg(orgId: Int): Future[List[RepoSHAIndex]] = {
    for {
      repos <- repoDataService.getReposForOrg(orgId)
      indexes <- dao.RepoSHAIndexTable.byRepo.lookupBatch(repos.map(_.repoId))
    } yield {
      indexes.values.flatten.toList
    }
  }

  def getIndexesForRepo(repoId: Int): Future[List[RepoSHAIndex]] = {
    dao.RepoSHAIndexTable.byRepo.lookup(repoId)
  }

  def getIndexesForRepo(repoIds: List[Int]): Future[Map[Int, List[RepoSHAIndex]]] = {
    dao.RepoSHAIndexTable.byRepo.lookupBatch(repoIds)
  }

  def getIndexesForRepoSHAs(repoId: Int, shas: List[String]): Future[List[RepoSHAIndex]] = {
    dao.RepoSHAIndexTable.byRepoSHA.lookupBatch(repoId, shas).map(_.toList.filterNot(_.deleted))
  }

  def getIndexId(indexId: Int): Future[Option[RepoSHAIndex]] = {
    getIndexIds(List(indexId)).map(_.headOption)
  }

  def getIndexIds(indexIds: List[Int]): Future[List[RepoSHAIndex]] = {
    dao.RepoSHAIndexTable.byId.lookupBatch(indexIds).map(_.values.flatten.toList)
  }

  def getDeletedIndexes(): Source[RepoSHAIndex, Any] = {
    dao.RepoSHAIndexTable.Streams.deleted
  }

  // latest (naive grab latest)
  // TODO: switch to tree storage
  def getLatestSHAIndexForRepos(repoIds: List[Int]): Future[Map[Int, RepoSHAIndex]] = {
    dao.RepoSHAIndexTable.byRepo.lookupBatch(repoIds).map {
      _.flatMap {
        case (k, vs) => vs.sortBy(_.created).lastOption.map { last =>
          k -> last
        }
      }
    }
  }

  def getLatestSHAIndex(repoId: Int): Future[Option[Int]] = {
    getLatestSHAIndexForRepos(List(repoId)).map(_.get(repoId).map(_.id))
  }

  def getAllSHAIndexesLatest(orgId: Int): Future[List[RepoSHAIndex]] = {
    for {
      repos <- repoDataService.getReposForOrg(orgId)
      indexes <- getLatestSHAIndexForRepos(repos.map(_.repoId)).map(_.values.toList)
    } yield {
      indexes
    }
  }

  def verifiedIndexIds(indexIds: List[Int], repoIds: Set[Int]): Future[List[Int]] = {
    for {
      indexes <- getIndexIds(indexIds)
    } yield {
      indexes.filter(i => repoIds.contains(i.repoId)).map(_.id)
    }

  }

  def writeIndex(idx: RepoSHAIndex)(implicit context: SpanContext): Future[RepoSHAIndex] = {
    for {
      index <- dao.RepoSHAIndexTable.insert(idx).map { id =>
        idx.copy(id = id)
      }
      wrapper = git.GitWriter.materializeIndex(index)
      _ <- indexerService.writeWrapper(idx.orgId, wrapper)
    } yield {
      index
    }

  }

  def setIndexRoot(indexId: Int, rootId: Int): Future[Unit] = {
    dao.RepoSHAIndexTable.updateRootById.update(indexId, Some(rootId)) map (_ => ())
  }

  def markIndexDeleted(indexId: Int): Future[Unit] = {
    dao.RepoSHAIndexTable.updateDeletedById.update(indexId, true) map (_ => ())
  }

  /**
   * Trees
   */
  def getSHAIndexTreeBatch(indexIds: List[Int]): Future[Map[Int, List[String]]] = {
    for {
      all <- dao.SHAIndexTreeTable.byIndex.lookupBatch(indexIds)
    } yield {
      all.map {
        case (k, v) => k -> v.map(_.file)
      }
    }
  }

  def writeTrees(indexId: Int, files: List[String], deleted: List[String]) = {
    val trees = files.map(f => SHAIndexTree(indexId, f, false))
    val deletedTrees = deleted.map(d => SHAIndexTree(indexId, d, true))
    for {
      _ <- dao.SHAIndexTreeTable.byIndex.delete(indexId)
      _ <- dao.SHAIndexTreeTable.insertBulk(trees)
      _ <- dao.SHAIndexTreeTable.insertBulk(deletedTrees)
    } yield {
      ()
    }
  }

  def deleteAnalysisTrees(indexId: Int) = {
    dao.AnalysisTreeTable.byIndex.delete(indexId)
  }

  def writeAnalysisTrees(trees: List[AnalysisTree]) = {
    dao.AnalysisTreeTable.insertBulk(trees)
  }

}
