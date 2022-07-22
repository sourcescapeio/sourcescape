package services

import models.{ IndexType, LocalRepoConfig, RemoteType, Sinks }
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, SourceQueue, Sink }
import akka.stream.{ OverflowStrategy, CompletionStrategy }
import akka.actor.{ Actor, Props, ActorRef }
import akka.pattern.{ ask, pipe }

// files
import akka.util.Timeout
import java.nio.file._
import org.joda.time._

import scala.jdk.CollectionConverters._
import models.Errors
import models.RepoCollectionIntent
import models.RepoWithSettings

@Singleton
class WatcherService @Inject() (
  configuration:        play.api.Configuration,
  repoDataService:      LocalRepoDataService,
  socketService:        SocketService,
  wsClient:             WSClient,
  applicationLifecycle: play.api.inject.ApplicationLifecycle)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  lazy val WatcherURL = configuration.get[String]("ozymandias.server")

  def disconnectAll(): Future[Unit] = {
    for {
      _ <- wsClient.url(WatcherURL + "/watches").withRequestTimeout(5000.millis).delete()
    } yield {
      ()
    }
  }

  def syncWatchForRepo(repo: RepoWithSettings): Future[Unit] = {
    val orgId = repo.repo.orgId
    val directory = repo.repo.asInstanceOf[LocalRepoConfig].localPath
    val repoId = repo.repo.repoId
    val repoName = repo.repo.repoName
    val intent = repo.defaultedIntent
    println("SYNCING:", directory, intent)
    for {
      _ <- withFlag(repo.shouldIndex) {
        socketService.watcherStartup(orgId, repoId, repoName)
      }
      response <- wsClient.url(WatcherURL + "/watches").withRequestTimeout(5000.millis).post(Json.obj(
        "watch" -> repo.shouldIndex,
        "orgId" -> orgId.toString(),
        "repoId" -> repoId.toString(),
        "directory" -> directory))
    } yield {
      if (response.status =/= 200) {
        println(s"Error starting watch for ${directory}.")
        println(response.body)
        throw new Exception("error starting watch")
      } else {
        response.body
      }
    }
  }

  /**
   * Other stuff
   * TODO: kind of unrelated, move this out of watcher service
   */
  def openFile(orgId: Int, repoKey: String, file: String, startLine: Int): Future[String] = {
    for {
      repo <- repoDataService.getRepo(models.RepoSHAHelpers.getRepoId(repoKey)).map {
        _.getOrElse(throw Errors.notFound("repo.dne", "Repo not found"))
      }
      sanitizedFile = (repo.localPath.replaceFirst("^/external", "") + "/" + file)
      _ <- wsClient.url(WatcherURL + "/launch").withRequestTimeout(5000.millis).post(Json.obj(
        "file" -> sanitizedFile,
        "startLine" -> startLine))
    } yield {
      sanitizedFile
    }
  }
}
