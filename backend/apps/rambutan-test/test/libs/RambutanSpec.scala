package test

import dal.{ SharedDataAccessLayer, LocalDataAccessLayer }
import services._
import models.index._
import models._
import models.graph._

import silvousplay.imports._

import org.scalatestplus.play.guice._
import org.scalatestplus.play._
import org.scalatest.{ BeforeAndAfterEach, BeforeAndAfterAll, Tag }
import org.mockito.{ MockitoSugar, ArgumentMatchersSugar }
import org.scalatest.matchers.should.Matchers

import sangria.macros._
import sangria.ast.Document

import play.api.test._
import play.api.test.Helpers._
import play.api.inject.guice._
import play.api.inject.bind
import play.api.libs.json._

import akka.stream.scaladsl._
import akka.stream.OverflowStrategy
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.actor.PoisonPill
import akka.actor.Props
import akka.Done

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

abstract class RambutanSpec extends PlaySpec
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  // with QueryHelpers
  // with IndexHelpers
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

  val dal = app.injector.instanceOf[SharedDataAccessLayer]
  val localDal = app.injector.instanceOf[LocalDataAccessLayer]
  val elasticSearchService = app.injector.instanceOf[ElasticSearchService]
  implicit val materializer = app.materializer

  protected def wipeElasticSearch() = {
    Source(IndexType.all).mapAsync(1) { it =>
      for {
        _ <- elasticSearchService.dropIndex(it.nodeIndexName)
        _ <- elasticSearchService.ensureIndex(it.nodeIndexName, GraphNode.mappings)
        _ = println(s"Wiped ${it.identifier} node index")
        _ <- elasticSearchService.dropIndex(it.edgeIndexName)
        _ <- elasticSearchService.ensureIndex(it.edgeIndexName, GraphEdge.mappings)
        _ = println(s"Wiped ${it.identifier} edge index")
      } yield {
        ()
      }
    }.runWith(Sink.ignore)
  }

  override def beforeAll() = {
    // do we need to flush all redis?
    val work1 = for {
      _ <- dal.dropDatabase()
      _ <- localDal.dropDatabase()
      // Let's see if we can add columns to tables safely
      _ <- dal.ensureDatabase()
      _ <- localDal.ensureDatabase()
      _ <- wipeElasticSearch()
      _ <- elasticSearchService.dropIndex(GenericGraphNode.globalIndex)
      _ <- elasticSearchService.ensureIndex(GenericGraphNode.globalIndex, GenericGraphNode.mappings)
      _ <- elasticSearchService.dropIndex(GenericGraphEdge.globalIndex)
      _ <- elasticSearchService.ensureIndex(GenericGraphEdge.globalIndex, GenericGraphEdge.mappings)
    } yield {
      ()
    }

    await(work1)
  }
}