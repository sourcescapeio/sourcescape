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
      // .overrides(bind[FileService].toInstance(mockFileService))
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
    "work" taggedAs (Tag("single")) in {
      // Add a scan directory

      curl.getString("/render-schema") { res =>
        println(res)
      }

      running(TestServer(testServerPort, app)) {

        curl.graphql(graphql"""
          {
            scans {
              path
            }
          }
        """) { res =>
          println(res)
        }

        val socket = curl.subscribe()
        await(socket.connected)

        // (id: "scan1Id")
        socket.pushG(
          graphql"""
            subscription ScanChanges {
              scanProgress {
                id
                progress
              }
            }
          """)

        curl.graphql(graphql"""
          mutation addScan {
            path1: createScan(path: "/Users/jierenchen/Projects") {
              id
              path
            }
          }
        """) { res =>
          println(res)
          // val scan1Id = (res3 \ "data" \ "path1" \ "id").as[Int]
        }

        val socketService = app.injector.instanceOf[SocketService]

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "scanProgress" \ "progress").asOpt[Int].getOrElse(0) > 50
            }
          }.map { _ =>
            println("50% SCANNED")
          }
        }

        curl.graphql(graphql"""
          {
            scans {
              id
              path
              progress
              repos {
                id
                name
                path
                intent
                indexes {
                  id
                  sha
                }
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "scanProgress" \ "progress").asOpt[Int] =?= Some(100)
            }
          }.map { _ =>
            println("COMPLETED SCAN")
          }
        }

        curl.graphql(graphql"""
          {
            scans {
              id
              path
              progress
              repos {
                id
                name
                path
                intent
                indexes {
                  id
                  sha
                }
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        socket.pushG(
          graphql"""
            subscription CloneChanges {
              cloneProgress {
                id
                progress
              }
            }
          """)

        socket.pushG(
          graphql"""
            subscription IndexChanges {
              indexProgress {
                id
                progress
              }
            }
          """)

        // Need to start up indexers here first before we schedule the mutation
        val webhookConsumerService = app.injector.instanceOf[WebhookConsumerService]
        val clonerService = app.injector.instanceOf[ClonerService]
        val indexerWorker = app.injector.instanceOf[IndexerWorker]
        val consumerF = webhookConsumerService.consumeOne()
        val clonerF = clonerService.consumeOne()
        val indexF = indexerWorker.consumeOne()

        // val consumerF2 = webhookConsumerService.consumeOne()
        // val clonerF2 = clonerService.consumeOne()
        // val indexF2 = indexerWorker.consumeOne()

        // do repo selection
        curl.graphqlU(s"""
          mutation SelectRepo {
            repo1: selectRepo(id: 5)
            repo2: selectRepo(id: 6)
          }
        """) { res =>
          println(res)
        }

        await(consumerF)

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "cloneProgress" \ "progress").asOpt[Int].getOrElse(0) > 50
            }
          }.map { _ =>
            println("50% CLONED")
          }
        }

        curl.graphql(graphql"""
          {
            scans {
              id
              path
              progress
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
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "cloneProgress" \ "progress").asOpt[Int] =?= Some(100)
            }
          }.map { _ =>
            println("CLONE COMPLETED")
          }
        }

        // make sure complete

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "indexProgress" \ "progress").asOpt[Int].getOrElse(0) > 20
            }
          }.map { _ =>
            println("20% INDEXED")
          }
        }

        curl.graphql(graphql"""
          {
            scans {
              id
              path
              progress
              repos {
                id
                name
                path
                indexes {
                  id
                  sha
                  cloneProgress
                  indexProgress                  
                }
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "indexProgress" \ "progress").asOpt[Int].getOrElse(0) > 40
            }
          }.map { _ =>
            println("40% INDEXED")
          }
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "indexProgress" \ "progress").asOpt[Int].getOrElse(0) > 60
            }
          }.map { _ =>
            println("60% INDEXED")
          }
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "indexProgress" \ "progress").asOpt[Int].getOrElse(0) > 80
            }
          }.map { _ =>
            println("80% INDEXED")
          }
        }

        await {
          socket.waitFor {
            case t: TextMessage.Strict => {
              (Json.parse(t.text) \ "data" \ "indexProgress" \ "progress").asOpt[Int] =?= Some(100)
            }
          }.map { _ =>
            println("INDEXING COMPLETED")
          }
        }

        // check again
        curl.graphql(graphql"""
          {
            scans {
              id
              path
              progress
              repos {
                id
                name
                path
                intent
                indexes {
                  id
                  sha
                }
              }
            }
          }
        """) { res =>
          println(Json.prettyPrint(res))
        }

        // await(consumerF2)
        // finish these off
        await(clonerF)
        await(indexF)

        /**
         * Second batch
         */
        // await(clonerF2)(Timeout(40.seconds))
        // await(indexF2)(Timeout(120.seconds))

        socket.close()
        await(socket.closed)
        println("CLOSED WEBSOCKET")
      }
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
      "primadonna.server" -> s"http://localhost:${3002}",
      "dorothy.server" -> s"http://localhost:${3004}",
      "redis.port" -> s"${6380}",
      "elasticsearch.port" -> s"${9201}",
      "slick.dbs.default.profile" -> "silvousplay.data.PostgresDriver$",
      "slick.dbs.default.db.url" -> s"jdbc:postgresql://localhost:${5433}/sourcescape?characterEncoding=UTF-8",
      "slick.dbs.default.db.user" -> "sourcescape",
      "slick.dbs.default.db.password" -> "sourcescape")
  }
}
