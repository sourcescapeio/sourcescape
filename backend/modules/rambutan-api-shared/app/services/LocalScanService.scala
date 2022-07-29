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
class LocalScanService @Inject() (
  configuration:          play.api.Configuration,
  socketService:          SocketService,
  repoDataService:        LocalRepoDataService,
  repoSyncService:        RepoSyncService,
  gitTreeIndexingService: GitTreeIndexingService,
  gitService:             LocalGitService,
  localDao:               dal.LocalDataAccessLayer)(implicit val ec: ExecutionContext, mat: akka.stream.Materializer) {
  // add directory (should trigger scan)

  def getScanById(id: Int): Future[Option[LocalScanDirectory]] = {
    localDao.LocalScanDirectoryTable.byId.lookup(id)
  }

  // def getScanByIds(ids: List[Long]) =
  def listScans(): Future[List[LocalScanDirectory]] = {
    localDao.LocalScanDirectoryTable.all
  }

  def createScan(orgId: Int, path: String, shouldScan: Boolean): Future[LocalScanDirectory] = {
    val obj = LocalScanDirectory(-1, 0, path)
    for {
      obj <- localDao.LocalScanDirectoryTable.insert(obj).map { id =>
        obj.copy(id = id)
      }
      _ <- withFlag(shouldScan) {
        initialScan(orgId, obj.id, path)
      }
    } yield {
      obj
    }
  }

  def initialScan(orgId: Int, scanId: Int, directory: String): Future[Unit] = {
    def progressCalc(idx: Long): Int = {
      val base = if (idx < 20) {
        (idx / 20.0) * 0.7
      } else {
        val denom = 1 + scala.math.exp(0.05 * -1 * idx.toInt)
        0.7 + ((1 / denom) * 0.3)
      }

      (base * 100).toInt
    }

    for {
      _ <- Future.successful(())
      gitDirectories = gitService.scanGitDirectory(directory).filter(_.valid)
      _ = println(gitDirectories)
      newDirectories <- gitDirectories.mapConcat { scanResult =>
        val remote = scanResult.remotes.flatMap { url =>
          val githubHttpsUrl = "https://github.com/([\\w\\.@\\:/\\-~]+).git".r
          val githubGitUrl = "git@github.com:([\\w\\.@\\:/\\-~]+).git".r
          val bitbucketHttpsUrl = "https://[^@]+@bitbucket.org/([\\w\\.@\\:/\\-~]+).git".r
          val bitbucketGitUrl = "git@bitbucket.org:([\\w\\.@\\:/\\-~]+).git".r
          url match {
            case githubHttpsUrl(a)    => Some((a, url, RemoteType.GitHub))
            case githubGitUrl(a)      => Some((a, url, RemoteType.GitHub))
            case bitbucketHttpsUrl(a) => Some((a, url, RemoteType.BitBucket))
            case bitbucketGitUrl(a)   => Some((a, url, RemoteType.BitBucket))
            case _                    => None
          }
        }.headOption

        remote.map {
          case (name, remote, remoteType) => {
            // LINK TO SCANID
            LocalRepoConfig(
              orgId,
              scanId,
              name,
              0,
              scanResult.localDir,
              remote,
              remoteType,
              branches = Nil)
          }
        }.toList
      }.zipWithIndex.groupedWithin(20, 500.milliseconds).mapAsync(1) {
        case items => {
          val idxs: List[Long] = items.map(_._2).toList
          println("IDX", idxs)
          for {
            _ <- withDefined(idxs.maxByOption(i => i)) { max =>
              socketService.scanProgress(orgId, scanId, progressCalc(max))
            }
          } yield {
            items.map(_._1)
          }
        }
      }.mapConcat(i => i).runWith(Sinks.ListAccum)
      _ = newDirectories.foreach(println)
      // throttle here
      _ <- repoDataService.upsertRepos(newDirectories)
      allDirectories = newDirectories.map(_.localPath).toSet
      // TODO: need to delete old
      // Need to fetch everything again to get repoId
      allRepos <- repoDataService.getAllLocalRepos()
      allReposCount = allRepos.length
      _ <- Source(allRepos).mapAsync(2) { r =>
        if (allDirectories.contains(r.localPath)) {
          for {
            shouldScan <- repoDataService.getRepoSettings(List(r.repoId)).map {
              _.getOrElse(r.repoId, None).map(_.intent =?= RepoCollectionIntent.Collect).getOrElse(false)
            }
            _ <- withFlag(shouldScan) {
              gitTreeIndexingService.updateBranchData(r)
            }
          } yield {
            ()
          }
        } else {
          Future.successful {
            println(s"Deleting repo ${r.localPath}")
          }
          // repoDataService.deleteRepo(r.id) // soft deletes?
        }
      }.runWith(Sink.ignore)
      _ <- socketService.scanFinished(orgId, scanId)
    } yield {
      ()
    }
  }
}
