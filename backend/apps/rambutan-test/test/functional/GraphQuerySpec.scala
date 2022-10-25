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
    "work" taggedAs (Tag("single")) in {
      await {
        runGraphIndex(IndexType.Javascript) {
          Node("n1", "require", name = Some("@nestjs/common"))
          Node("n2", "require")
          // edge(),
          // edge()
        } {
          Edge("e1", "butt", "n1", "n2")
        }
      }

      val data = await {
        dataForGraphQuery(IndexType.Javascript) {
          """
          root[
            type=require,
            name="@nestjs/common"
          ]
          """
        }
      }

      data.foreach(println)

    }
  }
}

// sbt "project rambutanTest" "testOnly test.GraphQuerySpecContainers"
class GraphQuerySpecContainers
  extends GraphQuerySpec
  with ForAllTestContainer {

  private val elasticsearch = ElasticsearchContainer(
    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.10.2"))

  private val postgres = PostgreSQLContainer(
    DockerImageName.parse("postgres:12.4"),
    databaseName = "sourcescape",
    username = "sourcescape",
    password = "sourcescape")

  private val redis = GenericContainer(
    "redis:5.0.10",
    exposedPorts = Seq(6379))

  private val primadonna = GenericContainer(
    "gcr.io/lychee-ai/sourcescape-cli-primadonna:0.2",
    waitStrategy = Wait.forLogMessage(".*node ./bin/www.*", 1),
    exposedPorts = Seq(3001))

  private val dorothy = GenericContainer(
    "gcr.io/lychee-ai/sourcescape-cli-dorothy:0.2",
    waitStrategy = Wait.forLogMessage(".*WEBrick::HTTPServer#start.*", 1),
    exposedPorts = Seq(3004))

  def config() = {
    Map(
      "primadonna.server" -> s"http://localhost:${primadonna.mappedPort(3001)}",
      "dorothy.server" -> s"http://localhost:${dorothy.mappedPort(3004)}",
      "redis.port" -> s"${redis.mappedPort(6379)}",
      "elasticsearch.port" -> s"${elasticsearch.mappedPort(9200)}",
      "slick.dbs.default.profile" -> "silvousplay.data.PostgresDriver$",
      "slick.dbs.default.db.url" -> s"jdbc:postgresql://localhost:${postgres.mappedPort(5432)}/sourcescape?characterEncoding=UTF-8",
      "slick.dbs.default.db.user" -> "sourcescape",
      "slick.dbs.default.db.password" -> "sourcescape")
  }

  override val container = MultipleContainers(
    postgres,
    elasticsearch,
    redis,
    primadonna,
    dorothy)
}

// sbt "project rambutanTest" "testOnly test.GraphQuerySpecCompose"
class GraphQuerySpecCompose
  extends GraphQuerySpec {

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
