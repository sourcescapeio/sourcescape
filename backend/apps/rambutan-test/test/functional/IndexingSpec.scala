package test

import models.index._
import models._
import models.query._
import models.graph._
import services._
import workers._

import silvousplay.imports._

import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import sangria.macros._
import org.scalatest.Tag

import play.api.test._
import play.api.test.Helpers._
import play.api.inject.guice._
import play.api.inject.bind
import play.api.libs.json._

import akka.stream.scaladsl.{ Source, Sink }
import akka.util.ByteString

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

import javax.inject._

import akka.http.scaladsl.model.ws._

abstract class IndexingSpec extends RambutanSpec {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  override def fakeApplication() = {
    val mockFileService = mock[FileService]
    val mockGitService = mock[LocalGitService]

    val mockGitContainer = mock[GitServiceRepo]

    val mockStaticAnalysisService = mock[StaticAnalysisService]

    when(mockGitContainer.scanResult()).thenReturn(Future.successful {
      GitScanResult("/example/test", valid = true, remotes = Set("git@github.com:jurchen/test.git"))
    })

    when(mockGitContainer.getRepoInfo).thenReturn(Future.successful {
      RepoInfo("sha", "hello", Some("main"), GitDiff.empty)
    })

    when(mockGitContainer.getCommitChain("sha")).thenReturn {
      Source(RepoCommit("sha", Nil, "hello", "main") :: Nil)
    }

    when(mockGitContainer.getTreeAt("sha")).thenReturn(Future.successful {
      Set.empty[String]
    })

    when(mockStaticAnalysisService.startDirectoryLanguageServer(any, any, any)).thenReturn(Future.successful(()))
    when(mockStaticAnalysisService.stopLanguageServer(any, any)).thenReturn(Future.successful(()))

    // when(mockGitContainer.getTreeAt("sha")).thenReturn(Future.successful {
    //   Set("test1.ts", "test2.ts")
    // })

    // when(mockGitContainer.getFilesAt(Set("test1.ts", "test2.ts"), "sha")).thenReturn(Future.successful{
    //   Map(
    //     "test1.ts" -> ByteString("function(){}"),
    //     "test2.ts" -> ByteString("function(){}")
    //   )
    // })

    when(mockGitService.getGitRepo("/example/test")).thenReturn(Future.successful(mockGitContainer))

    val renderedConfig = config().toSeq

    new GuiceApplicationBuilder()
      .configure(
        "application.router" -> "api.Routes",
        "use.watcher" -> false // should already be set, but just to be sure
      ).configure(
          renderedConfig: _*)
      .overrides(bind[LocalGitService].toInstance(mockGitService))
      .overrides(bind[FileService].toInstance(mockFileService))
      .overrides(bind[StaticAnalysisService].toInstance(mockStaticAnalysisService))
      .build()
  }

  override def beforeEach() = {
    // clear all indexed data sync
    val indexUpgradeService = app.injector.instanceOf[IndexUpgradeService]

    val redisService = app.injector.instanceOf[RedisService]

    val work = for {
      _ <- Source(IndexType.all).mapAsync(1) { it =>
        for {
          _ <- elasticSearchService.ensureIndex(it.nodeIndexName, GraphNode.mappings)
          _ = println(s"Ensured ${it.identifier} node index")
          _ <- elasticSearchService.ensureIndex(it.edgeIndexName, GraphEdge.mappings)
          _ = println(s"Ensured ${it.identifier} edge index")
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      _ <- indexUpgradeService.deleteAllIndexesSync()
      _ <- redisService.redisClient.flushall()
    } yield {
      ()
    }
    await(work)
  }

  "Scanning directories" should {
    // sbt "project rambutanTest" "testOnly test.IndexingSpecCompose -- -z work"
    "work" in {
      running(TestServer(testServerPort, app)) {
        val socket = curl.subscribe()
        await(socket.connected)

        println("CONNECTED")

        socket.pushG(
          graphql"""
            subscription CloneChanges {
              cloneProgress {
                indexId
                repoId
                progress
              }
            }
          """)

        socket.pushG(
          graphql"""
            subscription IndexChanges {
              indexProgress {
                indexId
                repoId
                progress
              }
            }
          """)

        // do repo selection
        curl.graphqlU(s"""
          mutation IndexRepo {
            repo1: indexRepo(directory: "/example/test")
          }
        """) { res =>
          println(res)
        }

        val socketWait = socket.waitFor {
          case t: TextMessage.Strict => {
            println(t.text)
            (Json.parse(t.text) \ "payload" \ "data" \ "cloneProgress" \ "progress").asOpt[Int].getOrElse(0) > 50
          }
        }.map { _ =>
          println("50% CLONED")
        }

        await(socketWait)

        curl.graphql(graphql"""
          {
            repos {
              id
              name
              path
              intent
              indexes {
                id
                sha
                cloneProgress
                indexProgress
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "payload" \ "data" \ "cloneProgress" \ "progress").asOpt[Int] =?= Some(100)
            }
          }.map { _ =>
            println("CLONE COMPLETED")
          }
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "payload" \ "data" \ "indexProgress" \ "progress").asOpt[Int] =?= Some(100)
            }
          }.map { _ =>
            println("INDEXING COMPLETED")
          }
        }

        // check again
        curl.graphql(graphql"""
          {
            repos {
              id
              name
              path
              intent
              indexes {
                id
                sha
                cloneProgress
                indexProgress
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        socket.close()
        await(socket.closed)
        println("CLOSED WEBSOCKET")
      }
    }
  }
}

class IndexingSpecCompose
  extends IndexingSpec {

  def config() = {
    Map(
      "primadonna.server" -> s"http://localhost:${3001}",
      "dorothy.server" -> s"http://localhost:${3004}",
      "redis.port" -> s"${6380}",
      "elasticsearch.port" -> s"${9201}",
      "slick.dbs.default.profile" -> "silvousplay.data.PostgresDriver$",
      "slick.dbs.default.db.url" -> s"jdbc:postgresql://localhost:${5433}/sourcescape?characterEncoding=UTF-8",
      "slick.dbs.default.db.user" -> "sourcescape",
      "slick.dbs.default.db.password" -> "sourcescape")
  }
}
