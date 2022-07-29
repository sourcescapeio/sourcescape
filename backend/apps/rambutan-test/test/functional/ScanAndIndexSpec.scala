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
import akka.http.scaladsl.model.ws._
import akka.Done
import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes

case class GraphQLWebSocket(
  connected: Future[Done],
  closed:    Future[Done],
  queue:     SourceQueueWithComplete[Message],
  sink:      ActorRef) {

  def close() = queue.complete()

  def push(item: Message) = {
    await(queue.offer(item))
  }

  def pushG(query: Document) = {
    val item = Json.obj(
      "query" -> query.renderCompact
    // "operation" ->
    )
    await(queue.offer(TextMessage(Json.stringify(item))))
  }

  def waitFor(f: PartialFunction[Message, Boolean]): Future[Unit] = {
    val promise = Promise[Unit]()

    for {
      r <- sink ? WebSocketClientActor.Subscribe(promise, f)
      rr <- r.asInstanceOf[Future[Unit]]
    } yield {
      rr
    }
  }
}

object WebSocketClientActor {
  case class Subscribe(p: Promise[Unit], f: PartialFunction[Message, Boolean])
  case class Received(item: Message)
}

class WebSocketClientActor() extends Actor {

  var items = List.empty[Message]

  var subscriptions = List.empty[(Promise[Unit], PartialFunction[Message, Boolean])]

  def receive = {
    // Add subscription
    case WebSocketClientActor.Subscribe(prom, filter) => {
      // early complete
      items.foreach { i =>
        if (filter.isDefinedAt(i) && filter(i)) {
          prom.trySuccess(())
        }
      }

      subscriptions = subscriptions.appended((prom, filter))

      sender() ! prom.future
    }
    // Received item as a sink
    case WebSocketClientActor.Received(item) => {
      subscriptions.foreach {
        case (p, f) if f.isDefinedAt(item) && f(item) => {
          p.trySuccess(())
        }
        case _ => ()
      }

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
      implicit val as = app.actorSystem

      val sinkActor = app.actorSystem.actorOf(Props(classOf[WebSocketClientActor]))
      val sink = Flow[Message].map { item =>
        WebSocketClientActor.Received(item)
      }.to(Sink.actorRef(sinkActor, PoisonPill)).mapMaterializedValue { _ =>
        Future.successful(Done)
      } // separate

      val (relay, source) = {
        Source.queue[Message](10, OverflowStrategy.dropHead).preMaterialize()
      }

      val flow: Flow[Message, Message, Future[Done]] = {
        Flow.fromSinkAndSourceMat(sink, source)(Keep.left)
      }

      val (upgradeResponse, closed) =
        Http().singleWebSocketRequest(WebSocketRequest("ws://localhost:" + testServerPort + GraphQLEndpoint), flow)

      val connected = upgradeResponse.map { upgrade =>
        // just like a regular http request we can access response status which is available via upgrade.response.status
        // status code 101 (Switching Protocols) indicates that server support WebSockets
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          Done
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }

      GraphQLWebSocket(connected, closed, relay, sinkActor)
    }

    def graphql[T](query: Document, expectedStatus: Int = 200)(f: JsValue => T) = {
      val req = FakeRequest(POST, GraphQLEndpoint).withBody(
        Json.obj(
          "query" -> query.renderCompact))
      val Some(result) = route(app, req)

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        f(contentAsJson(result))
      }
    }

    def graphqlU[T](query: String, expectedStatus: Int = 200)(f: JsValue => T) = {
      val req = FakeRequest(POST, GraphQLEndpoint).withBody(
        Json.obj(
          "query" -> query))
      val Some(result) = route(app, req)

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        f(contentAsJson(result))
      }
    }

    // def get[T](url: String, expectedStatus: Int = 200)(f: JsValue => T) = {
    //   val Some(result) = route(app, FakeRequest(GET, url))

    //   if (status(result) =/= expectedStatus) {
    //     throw new Exception(contentAsString(result))
    //   } else {
    //     f(contentAsJson(result))
    //   }
    // }

    def getString[T](url: String, expectedStatus: Int = 200)(f: String => T) = {
      val Some(result) = route(app, FakeRequest(GET, url))

      if (status(result) =/= expectedStatus) {
        throw new Exception(contentAsString(result))
      } else {
        f(contentAsString(result))
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
    // do we need to flush all redis?
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

        val p = socket.waitFor {
          case t: TextMessage.Strict => {
            println("RECEIVED MESSAGE", t.text)
            (Json.parse(t.text) \ "data" \ "scanProgress" \ "progress").asOpt[Int] =?= Some(100)
          }
        }

        await(p.map { _ =>
          println("COMPLETED SCANS")
        })

        curl.graphql(graphql"""
          {
            scans {
              id
              path
              repos {
                id
                name
                path
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

        // do repo selection
        curl.graphqlU(s"""
          mutation SelectRepos {
            path1: selectRepos(ids: [1, 2, 3]) {
              id
              path
            }
          }
        """) { res =>
          println(res)
          // val scan1Id = (res3 \ "data" \ "path1" \ "id").as[Int]
        }

        // check again

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
