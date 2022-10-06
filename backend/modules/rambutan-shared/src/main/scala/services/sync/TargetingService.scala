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
import akka.stream.scaladsl.Sink
import silvousplay.api.SpanContext

@Singleton
class TargetingService @Inject() (
  configuration:        play.api.Configuration,
  repoDataService:      RepoDataService,
  repoIndexDataService: RepoIndexDataService,
  gitService:           GitService,
  logService:           LogService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  private def indexObj(index: RepoSHAIndex, maybeWork: Option[WorkRecord]) = {
    maybeWork match {
      case Some(work) => Json.obj("work" -> true, "indexId" -> index.id)
      case _          => Json.obj("indexId" -> index.id)
    }
  }

  // def repoTargeting(orgId: Int, repoId: Int)(implicit context: SpanContext): Future[JsObject] = {
  //   for {
  //     repo <- repoDataService.getRepoWithSettings(orgId, repoId).map {
  //       _.getOrElse(throw new Exception("invalid repo"))
  //     }
  //     gitInfo <- gitService.withRepo(repo.repo) {
  //       _.getRepoInfo
  //     }
  //     sha = gitInfo.sha
  //     maybeDirty = if (gitInfo.isClean) None else Some(gitInfo.statusDiff)
  //     (index, maybeWork) <- repoSyncService.repoSHARefreshSync(repo, sha, maybeDirty)
  //   } yield {
  //     Json.obj(
  //       "repo" -> Json.obj(
  //         "repoId" -> repo.repo.repoId,
  //         "branches" -> repo.repo.branches,
  //         "repo" -> repo.repo.repoName,
  //         // sha info
  //         "dirty" -> index.dirty,
  //         "sha" -> index.sha,
  //         "shaMessage" -> gitInfo.shaMessage, // get from SHA
  //         "branch" -> gitInfo.currentBranch),
  //       "index" -> indexObj(index, maybeWork))
  //   }
  // }

  // def branchTargeting(orgId: Int, repoId: Int, branch: String)(implicit context: SpanContext): Future[JsObject] = {
  //   for {
  //     repo <- repoDataService.getRepoWithSettings(orgId, repoId).map {
  //       _.getOrElse(throw new Exception("invalid repo"))
  //     }
  //     commitChain <- gitService.withRepo(repo.repo) {
  //       _.getCommitChain(branch).runWith(Sink.headOption)
  //     }
  //     (sha, message) = commitChain match {
  //       case Some(c) => (c.sha, c.message)
  //       case None    => throw Errors.badRequest("invalid.branch", "Branch is invalid")
  //     }
  //     (index, maybeWork) <- repoSyncService.repoSHARefreshSync(repo, sha, None)
  //   } yield {
  //     Json.obj(
  //       "repo" -> Json.obj(
  //         "repoId" -> repo.repo.repoId,
  //         "branches" -> repo.repo.branches,
  //         "repo" -> repo.repo.repoName),
  //       "branch" -> Json.obj(
  //         "branch" -> branch,
  //         "sha" -> index.sha,
  //         "shaMessage" -> message,
  //         "dirty" -> false),
  //       "index" -> indexObj(index, maybeWork))
  //   }
  // }

  // def shaTargeting(orgId: Int, repoId: Int, sha: String)(implicit context: SpanContext): Future[JsObject] = {
  //   for {
  //     repo <- repoDataService.getRepoWithSettings(orgId, repoId).map {
  //       _.getOrElse(throw new Exception("invalid repo"))
  //     }
  //     shaObj <- repoIndexDataService.getSHA(repoId, sha).map {
  //       _.getOrElse(throw models.Errors.badRequest("sha.invalid", "Invalid sha"))
  //     }
  //     (index, maybeWork) <- repoSyncService.repoSHARefreshSync(repo, sha, None)
  //   } yield {
  //     Json.obj(
  //       "repo" -> Json.obj(
  //         "repoId" -> repo.repo.repoId,
  //         "branches" -> repo.repo.branches,
  //         "repo" -> repo.repo.repoName),
  //       "sha" -> Json.obj(
  //         "sha" -> sha,
  //         "shaMessage" -> shaObj.message,
  //         "dirty" -> false),
  //       "index" -> indexObj(index, maybeWork))
  //   }
  // }
}
