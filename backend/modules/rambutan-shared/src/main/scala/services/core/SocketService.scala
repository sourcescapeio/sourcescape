package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._

// import akka.http.scaladsl.coding.Gzip
import akka.stream.{ OverflowStrategy, QueueOfferResult }
import akka.stream.scaladsl.{ FileIO, Sink, Source, SourceQueue, Keep }
import akka.util.ByteString

import play.api.libs.json._

import akka.actor.{ Actor, Props, ActorRef }
import redis.actors.RedisSubscriberActor
import java.net.InetSocketAddress
import redis.api.pubsub.{ PMessage, Message }
import redis.RedisClient
import scala.concurrent.duration._
import org.joda.time._

class SubscribeActor(
  channels:   Seq[String],
  subscriber: ActorRef,
  host:       String,
  port:       Int) extends RedisSubscriberActor(
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

sealed abstract class SocketEventType(val identifier: String) extends Identifiable

object SocketEventType extends Plenumeration[SocketEventType] {
  // startup messages
  case object ScanStartup extends SocketEventType("scan-startup")
  case object ScanFinished extends SocketEventType("scan-finished")
  case object LocalRepoUpdate extends SocketEventType("local-update")
  case object IndexDeleted extends SocketEventType("index-deleted")
  case object ReposUpdated extends SocketEventType("repos-updated")

  // event messages
  case object WatcherStartup extends SocketEventType("watcher-startup")
  case object WatcherReady extends SocketEventType("watcher-ready")
  case object CloningStarted extends SocketEventType("cloning-started")
  case object CloningFinished extends SocketEventType("cloning-finished")
  case object IndexingStarted extends SocketEventType("indexing-started")
  case object IndexingFinished extends SocketEventType("indexing-finished")
  case object CachingUpdate extends SocketEventType("caching-update")
  case object CachingFinished extends SocketEventType("caching-finished")

  // not yet
  case object CompilationStarted extends SocketEventType("compilation-started")
  case object CompilationFinished extends SocketEventType("compilation-finished")
}

case class EventMessage(orgId: Int, additionalOrgIds: List[Int], eventType: SocketEventType, id: String, persist: Boolean, data: JsObject) {

  val allOrgIds = (orgId :: additionalOrgIds).toSet

  def redisKey = s"${eventType.identifier}:${id}"

  def toJson = Json.obj(
    "type" -> eventType,
    "id" -> id,
    "time" -> new DateTime().getMillis(),
    "persist" -> persist) ++ data
}

object EventMessage {
  implicit val format = Json.format[EventMessage]
}

private case class AddSourceQueue(orgIds: List[Int], q: SourceQueue[EventMessage])
private case class DropSourceQueue(queueKey: String)

private class Wrapper(implicit ec: ExecutionContext) extends Actor {
  val map = scala.collection.mutable.HashMap.empty[String, (SourceQueue[EventMessage], Set[Int])]

  def receive = {
    case item => {
      item match {
        case AddSourceQueue(orgIds, q) => {
          val queueKey = Hashing.uuid()
          println(s"Queue added ${queueKey}")
          map.addOne(queueKey -> (q, orgIds.toSet))
        }
        case DropSourceQueue(queueKey) => {
          println(s"Queue dropped ${queueKey}")
          map -= queueKey
        }
        case message: Message => {
          println(s"message received: ${message.data.utf8String}")
          Json.parse(message.data.utf8String).asOpt[EventMessage].foreach { eventMessage =>
            map.foreach {
              case (queueKey, (queue, orgIds)) if orgIds.intersect(eventMessage.allOrgIds).nonEmpty => {
                queue.offer(eventMessage) map {
                  case QueueOfferResult.QueueClosed => {
                    self ! DropSourceQueue(queueKey)
                  }
                  case QueueOfferResult.Failure(ex) => {
                    println(ex)
                    self ! DropSourceQueue(queueKey)
                  }
                  case i => println(i)
                } recover {
                  case i: akka.stream.StreamDetachedException => {
                    //Common websocket closure exception
                    //No need to log
                    self ! DropSourceQueue(queueKey)
                  }
                  case e => {
                    println(e)
                    self ! DropSourceQueue(queueKey)
                  }
                }
              }
              case _ => println("filtered")
            }
          }
        }
      }
    }
  }
}

@Singleton
class SocketService @Inject() (
  configuration: play.api.Configuration,
  redisService:  RedisService)(implicit actorSystem: akka.actor.ActorSystem, ec: ExecutionContext, mat: akka.stream.Materializer) {

  val redisClient = redisService.redisClient
  val RedisHost = redisService.RedisHost
  val RedisPort = redisService.RedisPort

  val RELAY = "relay"
  val channels = Seq(RELAY)

  // TODO: set actual dispatchers
  val subscriber = actorSystem.actorOf(
    Props(
      classOf[Wrapper],
      ec))

  val relay = actorSystem.actorOf(
    Props(
      classOf[SubscribeActor],
      channels,
      subscriber,
      RedisHost,
      RedisPort))

  /**
   * Local messages
   */
  def scanProgress(orgId: Int, scanId: Int, progress: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.ScanStartup, scanId.toString(), true, Json.obj("progress" -> progress)))
  }

  def scanFinished(orgId: Int, scanId: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.ScanStartup, scanId.toString(), true, Json.obj("progress" -> 100)))
  }

  def localRepoUpdate(orgId: Int, repoId: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.LocalRepoUpdate, "local-update", false, Json.obj("repoId" -> repoId)))
  }

  def watcherStartup(orgId: Int, repoId: Int, repo: String) = {
    publish(EventMessage(orgId, Nil, SocketEventType.WatcherStartup, repoId.toString, true, Json.obj("repo" -> repo)))
  }

  def watcherReady(orgId: Int, repoId: Int, repo: String) = {
    publish(EventMessage(orgId, Nil, SocketEventType.WatcherReady, repoId.toString, false, Json.obj("repo" -> repo)))
  }

  /**
   * Relevant to all
   */
  def reposUpdated(orgId: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.ReposUpdated, "repos-updated", false, Json.obj()))
  }

  def indexDeleted(orgId: Int, repoId: Int, indexId: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.IndexDeleted, "index-deleted", false, Json.obj("repoId" -> repoId, "indexId" -> indexId)))
  }

  // indexing records
  def cloningProgress(orgId: Int, additionalOrgIds: List[Int], repo: String, repoId: Int, indexId: Int, progress: Int) = {
    publish(
      EventMessage(
        orgId,
        Nil,
        SocketEventType.CloningStarted,
        indexId.toString,
        true,
        Json.obj("repo" -> repo, "repoId" -> repoId, "indexId" -> indexId, "progress" -> progress, "cloningProgress" -> progress)))
  }

  def cloningFinished(orgId: Int, additionalOrgIds: List[Int], repo: String, repoId: Int, indexId: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CloningStarted, indexId.toString, true, Json.obj("repoId" -> repoId, "indexId" -> indexId, "repo" -> repo, "progress" -> 100)))
  }

  def indexingProgress(orgId: Int, repo: String, repoId: Int, indexId: Int, progress: Int) = {
    publish(
      EventMessage(
        orgId,
        Nil,
        SocketEventType.IndexingStarted,
        indexId.toString,
        true,
        Json.obj("repo" -> repo, "repoId" -> repoId, "indexId" -> indexId, "progress" -> progress)))
  }

  def indexingFinished(orgId: Int, repo: String, repoId: Int, sha: String, indexId: Int) = {
    publish(
      EventMessage(
        orgId,
        Nil,
        SocketEventType.IndexingStarted,
        indexId.toString,
        true,
        Json.obj("repo" -> repo, "indexId" -> indexId, "repoId" -> repoId, "sha" -> sha, "progress" -> 100)))
  }

  def cachingAvailable(orgId: Int, workId: String, available: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CachingUpdate, workId, true, Json.obj("available" -> available)))
  }

  def cachingProgress(orgId: Int, workId: String, progress: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CachingUpdate, workId, true, Json.obj("progress" -> progress)))
  }

  def cachingFinished(orgId: Int, workId: String) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CachingFinished, workId, false, Json.obj()))
  }

  // don't worry about this for now
  def compilationProgress(orgId: Int, workId: String, progress: Int) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CompilationStarted, workId, true, Json.obj("progress" -> progress)))
  }

  def compilationFinished(orgId: Int, workId: String, repo: String) = {
    publish(EventMessage(orgId, Nil, SocketEventType.CompilationFinished, workId, false, Json.obj("repo" -> repo)))
  }

  //
  val MessageBufferKey = "message-buffer"
  private def publish(message: EventMessage) = {
    val rendered = Json.stringify(Json.toJson(message))
    for {
      _ <- if (message.persist) {
        for {
          _ <- redisClient.set(message.redisKey, rendered)
          _ <- redisClient.sadd(MessageBufferKey, message.redisKey)
        } yield {
          ()
        }
      } else {
        // delete
        for {
          _ <- redisClient.srem(MessageBufferKey, message.redisKey)
          _ <- redisClient.del(message.redisKey)
        } yield {
          ()
        }
      }
      _ <- redisClient.publish(RELAY, rendered)
    } yield {
      ()
    }
  }

  def getMessageBatch(ids: Seq[(SocketEventType, String)]): Future[Map[(SocketEventType, String), Option[EventMessage]]] = {
    val keys = ids.map {
      case (eventType, id) => s"${eventType.identifier}:${id}"
    }
    redisClient.mget[Array[Byte]](keys: _*).map { r =>
      ids.zip(r).map {
        case (id, obj) => id -> obj.flatMap { o =>
          Json.parse(o).asOpt[EventMessage]
        }
      }.toMap
    }
  }

  // def getMessage(eventType: SocketEventType, id: String): Future[Option[EventMessage]] = {
  //   for {
  //     raw <- redisClient.get(s"${eventType.identifier}:${id}")
  //   } yield {
  //     withDefined(raw) { r =>
  //       Json.parse(r.toArray).asOpt[EventMessage]
  //     }
  //   }
  // }

  // controller level
  def openSocket(orgIds: List[Int]): Future[Source[EventMessage, Any]] = {
    // get initial
    val queue = Source
      .queue[EventMessage](1000, OverflowStrategy.dropHead)

    val source = queue.mapMaterializedValue { q =>
      subscriber ! AddSourceQueue(orgIds, q)
    }
    for {
      members <- redisClient.smembers(MessageBufferKey)
      // TODO: do we need this buffreder anymore?
      buffer <- if (members.nonEmpty) {
        redisClient.mget(members.map(_.utf8String): _*).map { items =>
          val parsed = items.flatMap {
            case Some(item) => Json.parse(item.utf8String).asOpt[EventMessage]
            case _          => None
          }
          Source(parsed)
        }
      } else {
        Future.successful(Source.empty[EventMessage])
      }
    } yield {
      buffer.concat(source)
    }
  }
}
