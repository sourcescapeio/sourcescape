package controllers

import models._
import javax.inject._
import silvousplay.api._
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import play.api.libs.json._

@Singleton
class LocalRepoController @Inject() (
  configuration:    play.api.Configuration,
  telemetryService: TelemetryService,
  repoService:      services.RepoService,
  repoSyncService:  services.LocalRepoSyncService,
  repoDataService:  services.RepoDataService,
  socketService:    services.SocketService,
  gitService:       services.GitService,
  watcherService:   services.WatcherService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def openItem(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      withJson { body: RepoOpenForm =>
        watcherService.openFile(orgId, body.repoKey, body.file, body.startLine) map { f =>
          Json.obj("file" -> f)
        }
      }
    }
  }

  def refreshRepo(orgId: Int, repoId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      repoSyncService.repoRefreshAsync(orgId, repoId)
    }
  }

  val GitFiles = Set(".git/rebase-merge", ".git/COMMIT_EDITMSG", ".git/HEAD")
  def watcherUpdate(orgId: Int, repoId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      withJson { form: WebhookForm =>
        telemetryService.withTelemetry { implicit c =>
          val hasChange = form.changedFiles.exists { f =>
            IndexType.all.exists(_.isValidBlob(f)) || GitFiles.contains(f)
          }
          val gitUpdate = form.changedFiles.exists { f =>
            GitFiles.contains(f)
          }
          val shouldUpdate = form.defaultedForce || hasChange

          withFlag(!shouldUpdate) {
            println("Discarded: ")
            form.changedFiles.foreach(println)
          }
          withFlag(shouldUpdate) {
            for {
              repo <- repoDataService.getRepoWithSettings(orgId, repoId).map { f =>
                f.getOrElse(throw Errors.notFound("repo.dne", "Repo not found"))
              }
              gitInfo <- gitService.withRepo(repo.repo)(_.getRepoInfo)
              maybeDirty = if (gitInfo.isClean) None else Some(gitInfo.statusDiff)
              _ <- repoSyncService.repoSHARefreshSync(repo, gitInfo.sha, maybeDirty)
              _ <- withFlag(gitUpdate) {
                socketService.localRepoUpdate(orgId, repoId)
              }
            } yield {
              ()
            }
          }
        }
      }
    }
  }

  def finishedWatcherStart(orgId: Int, repoId: Int) = {
    api { implicit request =>
      println("FINISHED", repoId)
      for {
        localRepoConfig <- repoDataService.getRepo(repoId).map(_.getOrElse {
          throw Errors.notFound("repo.dne", "Repo not found")
        })
        _ <- socketService.watcherReady(orgId, repoId, localRepoConfig.repoName)
      } yield {
      }
    }
  }

}