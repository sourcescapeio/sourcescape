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
class RepoController @Inject() (
  configuration:        play.api.Configuration,
  telemetryService:     TelemetryService,
  authService:          services.AuthService,
  scanService:          services.LocalScanService,
  repoService:          services.RepoService,
  repoDataService:      services.RepoDataService,
  repoIndexDataService: services.RepoIndexDataService,
  socketService:        services.SocketService)(implicit ec: ExecutionContext, as: ActorSystem) extends API {

  def getRepoSummary(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedReposForOrg(orgId, RepoRole.Pull) { repos =>
        val filtered = repos.filter(_.orgId =?= orgId) // ignore public repos
        for {
          withSettings <- repoDataService.hydrateWithSettings(filtered)
          hydrated <- repoService.hydrateRepoSummary(withSettings)
        } yield {
          hydrated.map(_.dto)
        }
      }
    }
  }

  def getBranchSummary(orgId: Int, repoId: Int, branch: String) = {
    api { implicit request =>
      authService.authenticatedForRepo(orgId, repoId, RepoRole.Pull) {
        telemetryService.withTelemetry { implicit c =>
          repoService.getBranchSummary(orgId, repoId, java.net.URLDecoder.decode(branch, "UTF-8")).map(_.map(_.dto))
        }
      }
    }
  }

  def getRepoSHAs(orgId: Int, repoId: Int) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.ReadOnly) {
        repoIndexDataService.getSHAsForRepos(List(repoId)).map(_.map(_.dto))
      }
    }
  }

  // def scanRepos(orgId: Int) = {
  //   api { implicit request =>
  //     // can move to repoData
  //     authService.authenticatedForOrg(orgId, OrgRole.Admin) {
  //       scanService.initialScan(orgId)
  //     }
  //   }
  // }
}
