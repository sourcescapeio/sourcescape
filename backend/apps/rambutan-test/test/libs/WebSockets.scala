package test

import models.graph._

import silvousplay.imports._

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json._
import play.api.mvc.WebSocket

import sangria.macros._
import sangria.ast.Document

import akka.stream.scaladsl.Flow
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.actor.ActorRef
import akka.pattern.ask
import akka.actor.Actor
import akka.http.scaladsl.model.ws._
import akka.Done
import akka.NotUsed
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

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
      "payload" -> Json.obj(
        "query" -> query.renderCompact)
    // "operation" ->
    )
    await(queue.offer(TextMessage(Json.stringify(item))))
  }

  def waitFor(f: PartialFunction[Message, Boolean])(implicit ec: ExecutionContext): Future[Unit] = {
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

  var subscriptions = List.empty[(String, Promise[Unit], PartialFunction[Message, Boolean])]

  def receive = {
    // Add subscription
    case WebSocketClientActor.Subscribe(prom, filter) => {
      // early complete
      var shouldAdd = true
      items.foreach { i =>
        if (filter.isDefinedAt(i) && filter(i)) {
          prom.trySuccess(())
          shouldAdd = false
        }
      }

      if (shouldAdd) {
        subscriptions = subscriptions.appended((Hashing.uuid(), prom, filter))
      }

      sender() ! prom.future
    }
    // Received item as a sink
    case WebSocketClientActor.Received(item) => {
      val rmSet = subscriptions.flatMap {
        case (id, p, f) if f.isDefinedAt(item) && f(item) => {
          p.trySuccess(())
          Some(id)
        }
        case _ => None
      }.toSet

      subscriptions = subscriptions.filterNot {
        case (id, _, _) => rmSet.contains(id)
      }

      items = items.appended(item)
    }
  }
}
