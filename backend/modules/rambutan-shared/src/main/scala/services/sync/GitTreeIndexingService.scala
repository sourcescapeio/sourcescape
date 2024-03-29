package services

import models._
import models.graph._
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._

import akka.stream.scaladsl.{ Source, Sink, Flow }
import silvousplay.api.SpanContext

@Singleton
class GitTreeIndexingService @Inject() (
  configuration:        play.api.Configuration,
  gitService:           LocalGitService,
  indexerService:       IndexerService,
  repoDataService:      RepoDataService,
  repoIndexDataService: RepoIndexDataService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  // def indexRepo(repo: GenericRepo)

  def updateGitTreeToSha(repo: LocalRepoConfig, sha: String)(implicit context: SpanContext): Future[Unit] = {
    for {
      repoObj <- gitService.getGitRepo(repo.localPath)
      _ <- repoObj.getCommitChain(sha).groupedWithin(100, 1.second).mapAsync(1) { commits =>
        // filter out existing commits
        val keys = commits.map(repo.repoId -> _.sha)
        for {
          existingSHAs <- repoIndexDataService.getSHAsBatch(keys.toList).map {
            _.values.flatten.map(_.sha).toSet
          }
        } yield {
          commits.map(c => !existingSHAs.contains(c.sha) -> c)
        }
      }.mapConcat(i => i)
        .takeWhile(_._1)
        .map(_._2.repoSHA(repo.repoId, repo.branches, Nil))
        .groupedWithin(100, 1.second)
        .mapAsync(1) { shas =>
          repoIndexDataService.upsertSHAs(shas.toList) map (_ => shas)
        }
        .mapConcat(i => i)
        // scan, starting with root bloom
        .map { sha =>
          git.GitWriter.materializeCommit(sha, repo) // pass in bloom
        }
        .via(indexerService.wrapperFlow(repo.orgId, context))
        .runWith(Sink.ignore)
      _ = repoObj.close()
    } yield {
      ()
    }
  }

  // update branch data
  def updateBranchData(repo: LocalRepoConfig)(implicit context: SpanContext): Future[List[String]] = {
    for {
      repoObj <- gitService.getGitRepo(repo.localPath)
      branchMap <- repoObj.getRepoBranches
      branches = branchMap.keySet.toList
      // legacy
      _ <- repoDataService.updateBranches(repo.repoId, branchMap.keySet.toList)
      // index branches
      _ <- Source(branchMap).map {
        case (branch, sha) => git.GitWriter.materializeBranch(branch, sha, repo)
      }.via {
        indexerService.wrapperFlow(repo.orgId, context)
      }.runWith(Sink.ignore)
      // index full tree
      _ <- Source(branchMap).mapAsync(1) {
        case (branch, sha) => for {
          _ <- updateGitTreeToSha(repo, sha)
          // write out branch
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      _ = repoObj.close()
    } yield {
      branches
    }
  }
}
