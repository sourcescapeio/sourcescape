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

sealed abstract class RelationalQuerySpec
  extends RambutanSpec
  with IndexHelpers
  with QueryHelpers {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  import FileHelpers._

  val LocalRepo = LocalRepoConfig(-1, "/data/projects", 1, "/data/projects", "remote", RemoteType.GitHub, Nil)

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
    val repoDataService = app.injector.instanceOf[LocalRepoDataService]

    val redisService = app.injector.instanceOf[RedisService]

    val work = for {
      _ <- indexUpgradeService.deleteAllIndexesSync()
      _ <- wipeElasticSearch()
      _ <- redisService.redisClient.flushall()
      _ <- repoDataService.upsertRepo(LocalRepo)
    } yield {
      ()
    }
    await(work)
  }

  "Relational Queries" should {
    "SELECT" in {

      val CurrentIndex = RepoSHAIndex(
        id = 1,
        orgId = -1,
        repoName = "/data/projects",
        repoId = LocalRepo.repoId,
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
            directory("examples/003_grouped"): _*)
      }

      val (results, columns) = await {
        // CAT(A.name, "." , B.name)
        dataForRelationalQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None)) {
          """
            SELECT 
              A_Test.name AS A_Name,
              CAT(A_Test.name, CAT(B_Test.name, B_Test.name)) AS RR
            FROM
              root {
                type: "class"
              } AS A_Test
            TRACE join[A_Test]
              .linear_traverse [
                t["javascript::class_method"]
              ] AS B_Test
          """
        }
      }

      columns.foreach(i => println(Json.toJson(i)))

      results.foreach { d =>
        val a = d.getOrElse("A_Name", throw new Exception("fail"))
        val b = d.getOrElse("RR", throw new Exception("fail"))

        println(s"${a.as[String]}||${b.as[String]}")
      }
    }

    // sbt "project rambutanTest" "testOnly test.RelationalQuerySpecCompose -- -z groupby"
    "groupby" in {

      val CurrentIndex = RepoSHAIndex(
        id = 1,
        orgId = -1,
        repoName = "/data/projects",
        repoId = LocalRepo.repoId,
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
            directory("examples/003_grouped"): _*)
      }

      // Group all
      {
        val (results, columns) = await {
          dataForRelationalQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None)) {
            // should be able to group by both id and name
            """
              SELECT
                COUNT(Method.name) AS Method_Count,
                COUNT(Method.name) AS Method_Count_Again
              FROM
                root {
                  type: "class"
                } AS Class
              TRACE join[Class]
                .linear_traverse [
                  t["javascript::class_method"]
                ] AS Method
            """
          }
        }

        columns.foreach(i => println(Json.toJson(i)))

        results.foreach { d =>
          val a = d.getOrElse("Method_Count", throw new Exception("fail"))
          val b = d.getOrElse("Method_Count_Again", throw new Exception("fail"))

          println(s"${a.as[Int]}||${b.as[Int]}")
        }
      }

      // Group by key
      {
        val (results, columns) = await {
          dataForRelationalQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None)) {
            """
              SELECT
                Class,
                Class.name AS Class_Name,
                COUNT(Method.name) AS Method_Count,
                COUNT(Method.name) AS Method_Count_2
              FROM
                root {
                  type: "class"
                } AS Class
              TRACE join[Class]
                .linear_traverse [
                  t["javascript::class_method"]
                ] AS Method
            """
          }
        }

        columns.foreach(i => println(Json.toJson(i)))

        results.foreach { d =>
          val aa = d.getOrElse("Class", throw new Exception("fail"))
          val a = d.getOrElse("Class_Name", throw new Exception("fail"))
          val b = d.getOrElse("Method_Count", throw new Exception("fail"))
          val c = d.getOrElse("Method_Count_2", throw new Exception("fail"))

          println(s"${a.as[String]}||${b.as[Int]}||${c.as[Int]}")
          println((aa \ "terminus" \ "node" \ "extracted").as[String].take(100))
        }
      }

      // Ordering
      {
        val (results, columns) = await {
          dataForRelationalQuery(IndexType.Javascript, QueryTargetingRequest.AllLatest(None)) {
            // should be able to group by both id and name
            """
              SELECT
                Method.name AS Method_Name,
                COUNT(Method.name) AS Method_Count
              FROM
                root {
                  type: "class"
                } AS Class
              TRACE join[Class]
                .linear_traverse [
                  t["javascript::class_method"]
                ] AS Method
              ORDER BY Method_Count DESC, Method_Name
            """
          }
        }

        columns.foreach(i => println(Json.toJson(i)))

        results.foreach { d =>
          val a = d.getOrElse("Method_Name", throw new Exception("fail"))
          val b = d.getOrElse("Method_Count", throw new Exception("fail"))

          println(s"${a.as[String]}||${b.as[Int]}")
        }
      }
    }
  }
}

class RelationalQuerySpecCompose
  extends RelationalQuerySpec {

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
