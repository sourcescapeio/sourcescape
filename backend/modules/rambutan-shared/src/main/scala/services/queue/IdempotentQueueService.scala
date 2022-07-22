package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Sink, SourceQueue }
import akka.actor.{ Actor, Props, ActorRef }
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{ PMessage, Message }
import java.net.InetSocketAddress
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.util.ByteString

// trait HasQueueKey {
//   def key: String
// }

abstract class IdempotentQueue[O](val name: String) {

  def objectToKey(obj: O): String

  val queueKey = {
    s"${name}/queue"
  }

  val hashKey = {
    s"${name}/hash"
  }
}

private case class AddIdempotentQueue(name: String, q: SourceQueue[Unit])
private case class DropIdempotentQueue(name: String)

private class IdempotentSubscribeActor(
  channels:   Seq[String],
  subscriber: ActorRef,
  host:       String,
  port:       Int)
  extends RedisSubscriberActor(
    new InetSocketAddress(host, port),
    channels,
    Seq.empty[String],
    onConnectStatus = connected => { println(s"connected: $connected") }) {

  def onMessage(message: Message) = {
    subscriber ! message
  }

  def onPMessage(message: PMessage) = {
    // do nothing
  }
}

private case class IdempotentPing(name: String)

private object IdempotentPing {
  implicit val format = Json.format[IdempotentPing]
}

private class IdempotentRelayActor(implicit ec: ExecutionContext) extends Actor {
  // use map
  val map = scala.collection.mutable.Map.empty[String, SourceQueue[Unit]]

  def receive = {
    case item => {
      item match {
        case AddIdempotentQueue(name, q) => {
          println(s"Queue added ${name} ${q}")
          map += (name -> q)
        }
        case DropIdempotentQueue(name) => {
          println(s"Queue dropped ${name}")
          map -= name
        }
        case message: Message => {
          Json.parse(message.data.utf8String).asOpt[IdempotentPing].foreach { ping =>
            val name = ping.name
            map.get(name).foreach { q =>
              q.offer(()) map {
                case QueueOfferResult.QueueClosed => {
                  self ! DropIdempotentQueue(name)
                }
                case QueueOfferResult.Failure(ex) => {
                  println(ex)
                  self ! DropIdempotentQueue(name)
                }
                case i => println(i)
              } recover {
                case i: akka.stream.StreamDetachedException => {
                  //Common websocket closure exception
                  //No need to log
                  self ! DropIdempotentQueue(ping.name)
                }
                case e => {
                  println(e)
                  self ! DropIdempotentQueue(ping.name)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Singleton
class IdempotentQueueService @Inject() (
  configuration: play.api.Configuration,
  redisService:  RedisService)(implicit ec: ExecutionContext, actorSystem: akka.actor.ActorSystem) {

  val redisClient = redisService.redisClient

  val IDEMPOTENT_RELAY = "idempotent_relay"
  val channels = Seq(IDEMPOTENT_RELAY)

  val subscriber = actorSystem.actorOf(
    Props(
      classOf[IdempotentRelayActor],
      ec))

  actorSystem.actorOf(
    Props(
      classOf[IdempotentSubscribeActor],
      channels,
      subscriber,
      redisService.RedisHost,
      redisService.RedisPort))

  def enqueue[T](queue: IdempotentQueue[T], item: T)(implicit writes: Writes[T]): Future[Unit] = {
    // add item to individual queue
    val renderedPing = Json.stringify(Json.toJson(IdempotentPing(queue.name)))
    for {
      _ <- redisClient.rpush[String](queue.queueKey, Json.stringify(Json.toJson(item)))
      _ <- redisClient.publish(IDEMPOTENT_RELAY, renderedPing) // to trigger
    } yield {
      ()
    }
  }

  def clearQueue(queue: IdempotentQueue[_]): Future[Unit] = {
    // clear out working set
    for {
      _ <- redisClient.del(queue.queueKey)
      _ <- redisClient.del(queue.hashKey)
    } yield {
      ()
    }
  }

  def ack[T](queue: IdempotentQueue[T], items: List[T]): Future[Unit] = {
    redisClient.hdel(queue.hashKey, items.map(queue.objectToKey): _*) map (_ => ())
  }

  def source[T](queue: IdempotentQueue[T], tick: FiniteDuration = 5.minutes)(implicit reads: Reads[T]): Source[T, Any] = {
    val sourceQueue = Source
      .queue[Unit](1000, OverflowStrategy.backpressure) // may be more pings than queues

    val source = sourceQueue.mapMaterializedValue { q =>
      subscriber ! AddIdempotentQueue(queue.name, q)
    }

    // dequeue must be single threaded
    // no race condition possible because dequeue is single threaded
    // This does not account for a distributed world
    source.mapAsync(1) { _ =>
      // parse and extract
      for {
        maybeStr <- redisClient.lpop[String](queue.queueKey)
        // write out block on item
        maybeObj = withDefined(maybeStr) { str =>
          Json.fromJson[T](Json.parse(str)) match {
            case JsSuccess(item, _) => Option(item)
            case _                  => None // should error?
          }
        }
      } yield {
        maybeObj.toList
      }
    }.mapConcat(i => i).mapAsync(1) { obj =>
      // filter out already complete
      val key = queue.objectToKey(obj)
      for {
        alreadyDone <- redisClient.hget[ByteString](queue.hashKey, key)
        _ <- redisClient.hincrby(queue.hashKey, key, 1)
        emit = if (alreadyDone.isEmpty) {
          Option(obj)
        } else {
          println("BLOCKED", obj)
          None
        }
      } yield {
        println("EMIT", emit)
        emit
      }
    }.mapConcat(i => i.toList)
  }
}
