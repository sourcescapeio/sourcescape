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

abstract class GraphQuerySpec
  extends RambutanSpec
  with IndexHelpers
  with QueryHelpers {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  override def fakeApplication() = {
    val mockFileService = mock[FileService]
    val mockGitService = mock[LocalGitService]

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

  "Raw Graph Queries" should {
    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z nodes"
    "nodes" in {
      await {
        runGraphIndex(IndexType.Javascript)(
          Node("n1", "require", name = Some("@nestjs/common"), props = List(GenericGraphProperty("visibility", "private"))),
          Node("n2", "require", name = Some("jarvis")),
          Node("n3", "density", name = Some("jamila")))()
      }

      println("======")
      println("GRAPH1")
      println("======")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: [
              "require",
              "density"
            ]
          }
          """
        }
      }.foreach(println)

      println("======")
      println("GRAPH2")
      println("======")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            id: ["n1", "n2"]
          }
          """
        }
      }.foreach(println)

      println("======")
      println("GRAPH3")
      println("======")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "require",
            name: ["*common", "jarvis", "jamila"]
          }
          """
        }
      }.foreach(println)

      println("======")
      println("GRAPH4")
      println("======")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "require",
            props: {"visibility": "private"}
          }
          """
        }
      }.foreach(println)

    }

    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z linear.traverse"
    "linear.traverse" in {

      val N1 = "n1"
      val N2 = "n2"
      val N3 = "n3"
      val N4 = "n4"
      val N5 = "n5"

      // Data view
      // https://miro.com/app/board/uXjVPJhOq3s=/?share_link_id=681838518337
      await {
        runGraphIndex(IndexType.Javascript)(
          Node(N1, "require"),
          Node(N2, "require"),
          Node(N3, "require")
        // edge(),
        // edge()
        )(
            Edge("e1", "reference", N1, N4, name = Some("test")),
            Edge("e2", "reference", N3, N5)
          //
          )
      }

      println("=========")
      println("TRAVERSE1")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "require"
          }.linear_traverse [
            t("reference")
          ]
          """
        }
      }.foreach(println)

      // println("=========")
      // println("TRAVERSE2")
      // println("=========")

      // await {
      //   dataForGraphQuery(IndexType.Javascript) {
      //     """
      //     root {
      //       type: "require"
      //     }.linear_traverse {
      //       t {
      //         type: "reference",
      //         name: "test"
      //       }
      //     }
      //     """
      //   }
      // }.foreach(println)      

      // .linear_traverse {
      //           traverse: [
      //             ?[butt, { type: butt, name: "hello"}],
      //             ![butt],
      //             t[butt2]
      //           ]
      //         }.repeated_traverse {
      //           follow: [],
      //           repeat: [
      //             ?[butt],
      //             ...
      //           ]
      //         }
    }
  }
}

// sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose"
class GraphQuerySpecCompose
  extends GraphQuerySpec {

  def config() = {
    Map(
      // "primadonna.server" -> s"http://localhost:${3001}", // use 3002 to test against docker
      // "dorothy.server" -> s"http://localhost:${3004}",
      "redis.port" -> s"${6380}",
      "elasticsearch.port" -> s"${9201}",
      "slick.dbs.default.profile" -> "silvousplay.data.PostgresDriver$",
      "slick.dbs.default.db.url" -> s"jdbc:postgresql://localhost:${5433}/sourcescape?characterEncoding=UTF-8",
      "slick.dbs.default.db.user" -> "sourcescape",
      "slick.dbs.default.db.password" -> "sourcescape")
  }
}
