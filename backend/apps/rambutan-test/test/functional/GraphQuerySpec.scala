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

sealed abstract class GraphQuerySpec
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

    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z traverse.linear"
    "traverse.linear" in {

      val N1 = "n1"
      val N2 = "n2"
      val N3 = "n3"
      val N4 = "n4"
      val N5 = "n5"

      await {
        runGraphIndex(IndexType.Javascript)(
          Node(N1, "class"),
          Node(N2, "class"),
          Node(N3, "class"),
          Node(N4, "method"),
          Node(N5, "call"),
        // edge(),
        // edge()
        )(
            Edge("e1", "method", N1, N4, name = Some("test")),
            Edge("e2", "class-decorator", N3, N5)
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
            type: "class"
          }.linear_traverse [
            t["javascript::class_decorator"]
          ]
          """
        }
      }.foreach(println)

      println("=========")
      println("TRAVERSE2")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: ["method", "call"]
          }.linear_traverse [
            t["javascript::class_decorator".reverse]
          ]
          """
        }
      }.foreach(println)

      // , {
      //         type: "reference",
      //         name: "test"
      //       }

      println("=========")
      println("TRAVERSE3")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.linear_traverse [
            t[
              {
                type: "javascript::class_method",
                name: "test"
              }
            ]
          ]
          """
        }
      }.foreach(println)
    }


    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z traverse.follows"
    "traverse.follows" in {
      val NRoot = Range(0, 4).toArray.map { idx =>
        s"n${idx}"
      }
      val NRest = Range(4, 12).toArray.map { idx =>
        s"n${idx}"
      }
      val N = NRoot ++ NRest

      val allNodes = List(
        NRoot.map { s => Node(s, "class")},
        NRest.map { s => Node(s, "call")}
      ).flatten

      val allEdges = List(
        path(N(0), ("class-property", N(4)), ("class-decorator", N(5)), ("method", N(6))),
        path(N(1), ("class-decorator", N(7))),
        path(N(2), ("method", N(8))),
        path(N(3), ("class-decorator", N(9)), ("method", N(10)), ("method", N(11))) // should get partial
      ).flatten

      allNodes.foreach(println)
      allEdges.foreach(println)

      await {
        runGraphIndex(IndexType.Javascript)(
          allNodes:_*
        )(
          allEdges:_*
        )
      }

      println("=========")
      println("TRAVERSE1")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.linear_traverse [
            ?["javascript::class_property"],
            *["javascript::class_decorator"],
            t["javascript::class_method"]
          ]
          """
        }
      }.foreach(println)


      // what are the rules around duplicates?
      // println("=========")
      // println("TRAVERSE2")
      // println("=========")

      // await {
      //   dataForGraphQuery(IndexType.Javascript) {
      //     """
      //     root {
      //       type: "class"
      //     }.linear_traverse [
      //       *["javascript::class_method"],
      //       *["javascript::class_decorator"],
      //       *["javascript::class_method"],
      //       *["javascript::class_decorator"],
      //       t["javascript::class_method"]
      //     ]
      //     """
      //   }
      //   // what are the rules around duplicates? we do want to be able to parallelize it
      //   // we can revisit later
      // }.foreach(println)
    }


    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z traverse.follows2"
    "traverse.follows2" in {
      val NRoot = Range(0, 4).toArray.map { idx =>
        s"n${idx}"
      }
      val NRest = Range(4, 12).toArray.map { idx =>
        s"n${idx}"
      }
      val N = NRoot ++ NRest

      val allNodes = List(
        NRoot.map { s => Node(s, "class")},
        NRest.map { s => Node(s, "call")}
      ).flatten

      val allEdges = List(
        path(N(0), ("class-property", N(4)), ("class-decorator", N(5)), ("class-decorator", N(6))), // should emit 3
        path(N(1), ("class-decorator", N(7))), // should emit 1
        path(N(2), ("method", N(8))),
        path(N(3), ("class-decorator", N(9)), ("class-property", N(10))) // should emit 1 partial
      ).flatten

      allNodes.foreach(println)
      allEdges.foreach(println)

      await {
        runGraphIndex(IndexType.Javascript)(
          allNodes:_*
        )(
          allEdges:_*
        )
      }

      println("=========")
      println("TRAVERSE1")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.linear_traverse [
            ?["javascript::class_property"],
            *["javascript::class_decorator"]
          ]
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }

      await {
        // should omit initials
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.linear_traverse [
            ?["javascript::class_property"],
            *["javascript::class_decorator"]
          ].node_check {
            type: "call"
          }
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }

      println("=========")
      println("TRAVERSE2")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.repeated_traverse {
            follow : [
              ?["javascript::class_property"]
            ],
            repeat: [
              t["javascript::class_decorator"]
            ]
          }
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }

      println("=========")
      println("TRAVERSE3")
      println("=========")

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            type: "class"
          }.linear_traverse [
            t["javascript::class_decorator"],
            ?["javascript::class_property"]
          ]
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }      
    }

    // sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose -- -z traverse.loop"
    "traverse.loop" in {
      // Should not infinite loop

      val N = Range(0, 12).toArray.map { idx =>
        s"n${idx}"
      }

      val allNodes = N.map { s => Node(s, "class")}

      val allEdges = List(
        path(N(0), ("class-decorator", N(1)), ("class-decorator", N(2)), ("class-decorator", N(3))),
        path(N(3), ("class-decorator", N(0))),
      ).flatten

      allNodes.foreach(println)
      allEdges.foreach(println)

      await {
        runGraphIndex(IndexType.Javascript)(
          allNodes:_*
        )(
          allEdges:_*
        )
      }

      await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root {
            id: "n0"
          }.linear_traverse [
            *["javascript::class_decorator"]
          ]
          """
        }
      }.foreach { trace =>
        val s = (trace.tracesInternal :+ trace.terminus).flatMap { ti =>
          (ti.tracesInternal :+ ti.terminus).map(_.id)
        }.mkString("->")

        println(s)
      }
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
