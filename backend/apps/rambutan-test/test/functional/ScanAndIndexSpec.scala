package test

import play.api.test._
import play.api.test.Helpers._
import play.api.inject.guice._
import play.api.inject.bind
import org.scalatestplus.play.guice._
import org.scalatestplus.play._
import org.scalatest.{ BeforeAndAfterEach, BeforeAndAfterAll, Tag }

import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import dal.{ SharedDataAccessLayer, LocalDataAccessLayer }

import scala.concurrent.ExecutionContext.Implicits.global
import models.index._
import models._
import akka.stream.scaladsl.{ Source, Sink }
import akka.util.ByteString
import models.query._
import services._
import workers._
import play.api.libs.json._
import silvousplay.imports._

import org.mockito.{ MockitoSugar, ArgumentMatchersSugar }
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ ExecutionContext, Future }
import javax.inject._
import sangria.macros._
import sangria.ast.Document

abstract class RambutanSpec extends PlaySpec
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with QueryHelpers
  with IndexHelpers
  with MockitoSugar
  with ArgumentMatchersSugar {

  object curl {
    private val GraphQLEndpoint = "/graphql"
    def graphql(query: Document, expectedStatus: Int = 200) = {
      println(query.renderPretty)
      val req = FakeRequest(POST, GraphQLEndpoint).withBody(
        Json.obj(
          "query" -> query.renderCompact))
      val Some(result) = route(app, req)

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        contentAsJson(result)
      }
    }

    def get(url: String, expectedStatus: Int = 200) = {
      val Some(result) = route(app, FakeRequest(GET, url))

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        contentAsJson(result)
      }
    }

    def getString(url: String, expectedStatus: Int = 200) = {
      val Some(result) = route(app, FakeRequest(GET, url))

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        contentAsString(result)
      }
    }
  }

}

abstract class ScanAndIndexSpec extends RambutanSpec {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  override def fakeApplication() = {
    val mockFileService = mock[FileService]
    when(mockFileService.readFile(any)).thenReturn(Future.successful(ByteString("")))

    val renderedConfig = config().toSeq

    new GuiceApplicationBuilder()
      .configure(
        "application.router" -> "api.Routes",
        "use.watcher" -> false // should already be set, but just to be sure
      ).configure(
          renderedConfig: _*)
      .overrides(bind[FileService].toInstance(mockFileService))
      .build()
  }

  // override def afterStart() = {

  // }

  override def beforeAll() = {
    val dal = app.injector.instanceOf[SharedDataAccessLayer]
    val localDal = app.injector.instanceOf[LocalDataAccessLayer]
    val elasticSearchService = app.injector.instanceOf[ElasticSearchService]
    implicit val materializer = app.materializer
    val work1 = for {
      _ <- dal.dropDatabase()
      _ <- localDal.dropDatabase()
      // Let's see if we can add columns to tables safely
      _ <- dal.ensureDatabase()
      _ <- localDal.ensureDatabase()
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
    } yield {
      ()
    }

    await(work1)
  }

  override def beforeEach() = {
    // clear all indexed data sync
    val indexUpgradeService = app.injector.instanceOf[IndexUpgradeService]
    val work = indexUpgradeService.deleteAllIndexesSync()
    await(work)
  }

  "Scanning directories" should {
    "work" taggedAs (Tag("single")) in {
      // Add a scan directory

      val res = curl.getString("/render-schema")

      println(res)

      // val query = graphqlInput"""
      //   {
      //     name
      //     friends {
      //       id
      //       name
      //     }
      //   }
      // """
      val res2 = curl.graphql(graphql"""
        {
          scans {
            path
          }
        }
      """)

      println(res2)

      val res3 = curl.graphql(graphql"""
        mutation addScan {
          path1: createScan(path: "/Users/test") {
            id
            path
          },
          path2: createScan(path: "/Users/test2") {
            id
            path
          }
        }
      """)

      // open socket

      println(res3)

      val res4 = curl.graphql(graphql"""
        {
          scans {
            id
            path
          }
        }
      """)

      println(Json.stringify(res4))

      // val Some(result) = route(app, FakeRequest(GET, "/health"))

      // // we actually want to set a timeout at each level?
      // for {
      //   test <- curl.graphql(s"""

      //   """)
      // } yield {

      // }

      // Index repo

      // Run search and have it work
    }
  }
}

/**
 * Two types of ways for running now
 *
 */

// sbt "project rambutanTest" "testOnly test.ScanAndIndexSpecContainers"
class ScanAndIndexSpecContainers
  extends ScanAndIndexSpec
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

// sbt "project rambutanTest" "testOnly test.ScanAndIndexSpecCompose"
class ScanAndIndexSpecCompose
  extends ScanAndIndexSpec {

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
