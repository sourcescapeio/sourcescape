package test

import dal.{ SharedDataAccessLayer, LocalDataAccessLayer }
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
import org.scalatestplus.play._

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
import org.joda.time.DateTime
import akka.stream.Materializer

sealed abstract class SrcLogQuerySpec
  extends RambutanSpec
  with IndexHelpers
  with QueryHelpers {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  import FileHelpers._

  override def fakeApplication() = {
    val mockFileService = mock[FileService]
    val mockGitService = mock[LocalGitService]
    when(mockGitService.scanGitDirectory(any)).thenReturn(Source(
      List(
        GitScanResult("/data/projects/project1", true, Set("git@github.com:org/project1.git")))))

    val renderedConfig = config().toSeq

    new GuiceApplicationBuilder()
      .configure(
        "application.router" -> "api.Routes",
        "use.watcher" -> false // should already be set, but just to be sure
      ).configure(
          renderedConfig: _*)
      .overrides(bind[LocalGitService].toInstance(mockGitService))
      .overrides(bind[FileService].toInstance(mockFileService))
      .build()
  }

  override def beforeEach() = {
    // clear all indexed data sync
    val indexUpgradeService = app.injector.instanceOf[IndexUpgradeService]

    val redisService = app.injector.instanceOf[RedisService]

    val work = for {
      _ <- indexUpgradeService.deleteAllIndexesSync()
      _ <- wipeElasticSearch()
      _ <- redisService.redisClient.flushall()
    } yield {
      ()
    }
    await(work)
  }

  "Scanning directories" should {

    // sbt "project rambutanTest" "testOnly test.SrcLogQuerySpecCompose -- -z basic"
    "basic" in {
      curl.graphql(graphql"""
        mutation addScan {
          path1: createScan(path: "/data/projects") {
            id
            path
          }
        }
      """) { res =>
        println(res)
      }

      val repoId = curl.graphql(graphql"""
        {
          repos {
            id
            path
          }
        }
      """) { res =>
        (res \ "data" \ "repos" \\ "id").map(_.as[Int])(0)
      }

      await {
        runTestIndex(
          RepoSHAIndex(
            id = 1,
            orgId = -1,
            repoName = "/data/projects",
            repoId = repoId,
            sha = "123",
            rootIndexId = None,
            dirtySignature = None,
            workId = "123",
            deleted = false,
            created = new DateTime().getMillis()),
          IndexType.Javascript)(
            directory("examples/001_basic"): _*)
      }

      val data = await {
        dataForQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None))(
          """
            javascript::require(E)[name="@nestjs/common"].

            javascript::class_decorator(A, B).
            javascript::call(D, B).
            javascript::member(E, D)[name="Controller"].
            javascript::call_arg(B, C)[index=1].

            javascript::class_method(A, F).
            javascript::method_decorator(F, G).
            javascript::call(H, G).
            javascript::member(E, H)[name="Post"].

            %root(H).
            %select(A, B, F, G).
          """)
      }

      assert(data.length === 1)
      val firstItem = data(0)

      mapAssert(firstItem, "A") { obj =>
        (obj \ "terminus" \ "node" \ "name").as[String] mustEqual "Hello"
        (obj \ "terminus" \ "node" \ "type").as[String] mustEqual "class"
      }

      mapAssert(firstItem, "B") { obj =>
        (obj \ "terminus" \ "node" \ "extracted").as[String] mustEqual "Controller('app')"
        (obj \ "terminus" \ "node" \ "type").as[String] mustEqual "call"
      }

      mapAssert(firstItem, "F") { obj =>
        (obj \ "terminus" \ "node" \ "name").as[String] mustEqual "doSomething"
        (obj \ "terminus" \ "node" \ "type").as[String] mustEqual "method"
      }

      mapAssert(firstItem, "G") { obj =>
        (obj \ "terminus" \ "node" \ "extracted").as[String] mustEqual "Post('/test')"
        (obj \ "terminus" \ "node" \ "type").as[String] mustEqual "call"
      }

      // data.foreach { d =>
      //   println("====================")
      //   println("RESULT")
      //   println("====================")
      //   d.map {
      //     case (k, v) => {
      //       println(k)
      //       println(Json.prettyPrint((v \ "terminus" \ "node").as[JsValue]))
      //       println((v \ "terminus" \ "node" \ "extracted").as[String])
      //       println((v \ "terminus" \ "node" \ "nearby" \ "code").as[String])
      //     }
      //   }
      // }
    }

    // sbt "project rambutanTest" "testOnly test.SrcLogQuerySpecCompose -- -z all.calls"
    "all.calls" in {
      curl.graphql(graphql"""
        mutation addScan {
          path1: createScan(path: "/data/projects") {
            id
            path
          }
        }
      """) { res =>
        println(res)
      }

      val repoId = curl.graphql(graphql"""
        {
          repos {
            id
            path
          }
        }
      """) { res =>
        (res \ "data" \ "repos" \\ "id").map(_.as[Int])(0)
      }

      val CurrentIndex = RepoSHAIndex(
        id = 1,
        orgId = -1,
        repoName = "/data/projects",
        repoId = repoId,
        sha = "123",
        rootIndexId = None,
        dirtySignature = None,
        workId = "123",
        deleted = false,
        created = new DateTime().getMillis())

      await {
        runTestIndex(
          CurrentIndex,
          IndexType.Javascript)(
            directory("examples/001_basic"): _*)
      }

      await {
        dataForGraphQuery(IndexType.Javascript, index = CurrentIndex) {
          """
          root {
            all
          }.linear_traverse[
            t["javascript::call_link"]
          ]
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }

      val data = await {
        dataForQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None))(
          """
            javascript::all_called(FZERO, F).
            javascript::contains(F, WARNCALL).

            javascript::member(CONSOLE, WARN)[name = "warn"].
            javascript::call(WARN, WARNCALL).
          """)
      }.foreach { d =>
        println("====================")
        println("RESULT")
        println("====================")
        d.map {
          case (k, v) => {
            println(k)
            println(Json.prettyPrint((v \ "terminus" \ "node").as[JsValue]))
            println((v \ "terminus" \ "node" \ "extracted").as[String])
            println((v \ "terminus" \ "node" \ "nearby" \ "code").as[String])
          }
        }
      }
    }

  }
}

class SrcLogQuerySpecCompose
  extends SrcLogQuerySpec {

  def config() = {
    Map(
      "primadonna.server" -> s"http://localhost:${3001}", // use 3002 to test against docker
      "dorothy.server" -> s"http://localhost:${3004}",
      "redis.port" -> s"${6380}",
      "elasticsearch.port" -> s"${9201}",
      "slick.dbs.default.profile" -> "silvousplay.data.PostgresDriver$",
      "slick.dbs.default.db.url" -> s"jdbc:postgresql://localhost:${5433}/sourcescape?characterEncoding=UTF-8",
      "slick.dbs.default.db.user" -> "sourcescape",
      "slick.dbs.default.db.password" -> "sourcescape")
  }
}
