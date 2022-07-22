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

trait HasQueueKey {
  def key: String
}

abstract class BucketPrioritizedQueue[O, T, K](val name: String) {
  val popAll: Boolean = false

  val WorkingSetKey = s"${name}/current"

  def getObjects(): Future[List[O]]

  def objectToKeys(obj: O): List[K]

  def itemToKey(item: T): K

  def keyToId(k: K): String
  def idToKey(id: String): K

  def queueKey(key: K): String = {
    s"${this.name}-queue/${keyToId(key)}"
  }

  def prioritize(keys: List[K], workingSet: List[K]): List[K]

  def compress(items: List[T]): List[T] = {
    items
  }

  // def queueKey(item: T) = {
  //   s"${name}/queue/${item.key}"
  // }
}

private case class AddBucketQueue(name: String, q: SourceQueue[Unit])
private case class DropBucketQueue(name: String)

private class BucketSubscribeActor(
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

private case class BucketPing(name: String)

private object BucketPing {
  implicit val format = Json.format[BucketPing]
}

private class BucketRelayActor(implicit ec: ExecutionContext) extends Actor {
  // use map
  val map = scala.collection.mutable.Map.empty[String, SourceQueue[Unit]]

  def receive = {
    case item => {
      item match {
        case AddBucketQueue(name, q) => {
          println(s"Queue added ${name} ${q}")
          map += (name -> q)
        }
        case DropBucketQueue(name) => {
          println(s"Queue dropped ${name}")
          map -= name
        }
        case message: Message => {
          Json.parse(message.data.utf8String).asOpt[BucketPing].foreach { ping =>
            val name = ping.name
            map.get(name).foreach { q =>
              q.offer(()) map {
                case QueueOfferResult.QueueClosed => {
                  self ! DropBucketQueue(name)
                }
                case QueueOfferResult.Failure(ex) => {
                  println(ex)
                  self ! DropBucketQueue(name)
                }
                case i => println(i)
              } recover {
                case i: akka.stream.StreamDetachedException => {
                  //Common websocket closure exception
                  //No need to log
                  self ! DropBucketQueue(ping.name)
                }
                case e => {
                  println(e)
                  self ! DropBucketQueue(ping.name)
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
class BucketPrioritizedQueueService @Inject() (
  configuration: play.api.Configuration,
  redisService:  RedisService)(implicit ec: ExecutionContext, actorSystem: akka.actor.ActorSystem) {

  val redisClient = redisService.redisClient

  val BUCKET_RELAY = "bucket_relay"
  val channels = Seq(BUCKET_RELAY)

  val subscriber = actorSystem.actorOf(
    Props(
      classOf[BucketRelayActor],
      ec))

  actorSystem.actorOf(
    Props(
      classOf[BucketSubscribeActor],
      channels,
      subscriber,
      redisService.RedisHost,
      redisService.RedisPort))

  def enqueue[O, T, K](queue: BucketPrioritizedQueue[O, T, K], item: T)(implicit writes: Writes[T]): Future[Unit] = {
    // add item to individual queue
    val rendered = Json.stringify(Json.toJson(BucketPing(queue.name)))
    val queueKey = queue.queueKey(queue.itemToKey(item))
    for {
      _ <- redisClient.rpush[String](queueKey, Json.stringify(Json.toJson(item)))
      _ <- redisClient.publish(BUCKET_RELAY, rendered) // to trigger
    } yield {
      ()
    }
  }

  def initializeQueues[O, T, K](queue: BucketPrioritizedQueue[O, T, K]): Future[Unit] = {
    // clear out working set
    for {
      _ <- redisClient.del(queue.WorkingSetKey)
    } yield {
      ()
    }
  }

  def ack[O, T, K](queue: BucketPrioritizedQueue[O, T, K], item: T): Future[Unit] = {
    redisClient.hincrby(
      queue.WorkingSetKey,
      queue.keyToId(queue.itemToKey(item)),
      -1) map (_ => ())
  }

  def clearQueue[O, T, K](queue: BucketPrioritizedQueue[O, T, K], obj: O) = {
    Future.sequence {
      queue.objectToKeys(obj).map(queue.queueKey).map(i => redisClient.del(i))
    } map (_ => ())
  }

  def source[O, T, K](queue: BucketPrioritizedQueue[O, T, K], tick: FiniteDuration = 5.minutes)(implicit reads: Reads[T]): Source[T, Any] = {

    // first one will fire off fast
    // subsequent will dedupe off first
    val sourceQueue = Source
      .queue[Unit](1000, OverflowStrategy.dropHead)
      .map(_ => ())

    val source = sourceQueue.mapMaterializedValue { q =>
      subscriber ! AddBucketQueue(queue.name, q)
    }

    // dequeue must be single threaded
    // no race condition possible because dequeue is single threaded
    source.merge(Source.tick(5.seconds, tick, ())).mapAsync(1) { _ =>
      println(s"TICK ${queue.name}")
      // Assumption: objects is small (< 100)
      queue.getObjects()
    }.mapAsync(1) { allObjects =>
      val allKeys = allObjects.flatMap(queue.objectToKeys)

      for {
        workingSet <- redisClient.hgetall[String](queue.WorkingSetKey).map(_.filter(_._2.toInt > 0).keySet).map(_.toList.map(queue.idToKey))
      } yield {
        queue.prioritize(allKeys, workingSet)
      }
    }.mapConcat(i => i).mapAsync(1) { r =>
      val queueKey = queue.queueKey(r)

      for {
        popped <- if (queue.popAll) {
          val transaction = redisClient.multi()
          val lrange = transaction.lrange[String](queueKey, 0, -1)
          transaction.del(queueKey)
          transaction.exec()
          lrange.map(_.toList)
        } else {
          redisClient.lpop[String](queueKey).map(_.toList)
        }
      } yield {
        val base = popped.flatMap { str =>
          Json.fromJson[T](Json.parse(str)) match {
            case JsSuccess(item, _) => List(item)
            case _ => {
              // should throw?
              Nil
            }
          }
        }

        queue.compress(base)
      }
    }.mapConcat(i => i).mapAsync(1) { item =>
      val id = queue.keyToId(queue.itemToKey(item))
      redisClient.hincrby(queue.WorkingSetKey, id, 1) map { _ =>
        item
      }
    }
  }
}
