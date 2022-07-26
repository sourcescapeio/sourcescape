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

import scala.concurrent.{ ExecutionContext, Future, Promise }
import javax.inject._
import sangria.macros._
import sangria.ast.Document

import play.api.mvc.WebSocket
import akka.stream.scaladsl.Flow
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.SourceQueue
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.actor.PoisonPill
import akka.actor.ActorRef
import akka.pattern.ask
import akka.actor.Props
import akka.actor.Actor
import play.api.http.websocket.TextMessage
import play.api.http.websocket.BinaryMessage

case class WebSocketResponse(
  queue: SourceQueue[WebSocketClient.ExtendedMessage],
  // sink
  sinkActor:        ActorRef,
  disconnectFuture: Future[Unit],
  client:           WebSocketClient) {

  def push(items: List[WebSocketClient.ExtendedMessage]) = {
    sinkActor ! WebSocketClientActor.Push(items)
  }

  def pull() = {
    (sinkActor ? WebSocketClientActor.Pull).map { i =>
      i.asInstanceOf[List[WebSocketClient.ExtendedMessage]]
    }
  }

  def shutdown() = {
    // client.shutdown()
    disconnectFuture
  }
}

object WebSocketClientActor {
  case class Push(items: List[WebSocketClient.ExtendedMessage])
  case object Pull
  case class Received(item: WebSocketClient.ExtendedMessage)
}

class WebSocketClientActor() extends Actor {

  var items = List.empty[WebSocketClient.ExtendedMessage]

  def receive = {
    // client facing
    case WebSocketClientActor.Pull => {
      sender() ! items
      items = List.empty[WebSocketClient.ExtendedMessage]
    }
    // internal
    case WebSocketClientActor.Received(item) => {
      println(item)
      items = items.appended(item)
    }
  }
}

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

    // use a subscribe actor I suppose
    //Source.actorRef
    //Sink.actorRef
    def subscribe() = {
      // create actor
      val source = Source.queue[WebSocketClient.ExtendedMessage](10, OverflowStrategy.dropHead)

      import app.materializer
      val relay = source.toMat(Sink.ignore)(Keep.left).run()
      val sinkActor = app.actorSystem.actorOf(Props(classOf[WebSocketClientActor]))
      val sink = Flow[WebSocketClient.ExtendedMessage].map { item =>
        WebSocketClientActor.Received(item)
      }.to(Sink.actorRef(sinkActor, PoisonPill)) // separate

      val (disconnectFuture, client) = WebSocketClient { client =>
        val url = java.net.URI.create("ws://localhost:" + testServerPort + GraphQLEndpoint)
        val dF = client.connect(url, subprotocol = None) { (headers, flow) =>

          source.via(flow).runWith(sink)
        }

        (dF, client)
      }

      WebSocketResponse(relay, sinkActor, disconnectFuture, client)
    }

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

      // val res = curl.getString("/render-schema")

      // println(res)

      val socket = curl.subscribe()
      socket.push(WebSocketClient.SimpleMessage(
        TextMessage(Json.stringify(Json.obj("a" -> "b"))),
        false) :: Nil)
      // needs to return an object that contains both the sourcequeue and sink that you can control when to close
      // you close the source, not the

      // socket.push(

      // )
      // await(socket.pull())

      // create a socket
      // new WebSocketClient

      // val res2 = curl.graphql(graphql"""
      //   {
      //     scans {
      //       path
      //     }
      //   }
      // """)

      // println(res2)

      // val res3 = curl.graphql(graphql"""
      //   mutation addScan {
      //     path1: createScan(path: "/Users/test") {
      //       id
      //       path
      //     },
      //     path2: createScan(path: "/Users/test2") {
      //       id
      //       path
      //     }
      //   }
      // """)

      // // open socket

      // println(res3)

      // // await(socket.shutdown())

      // val res4 = curl.graphql(graphql"""
      //   {
      //     scans {
      //       id
      //       path
      //     }
      //   }
      // """)

      // println(Json.stringify(res4))
      // // HOW TO DISCONNECT?

      Thread.sleep(10000)
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
