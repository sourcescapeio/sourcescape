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
import play.api.inject.ApplicationLifecycle
import java.util.Base64

import akka.pattern.ask
import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import akka.stream.scaladsl.{ Source, RestartSource, Sink, Keep, DelayStrategy }
import akka.stream.{ KillSwitches, KillSwitch, DelayOverflowStrategy }
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider

private case class AddQueue(key: String, killSwitch: KillSwitch)
private case class DropQueue(key: String)
private case object ListQueues
private case object WipeQueues

private class QueueStateActor extends Actor {
  val map = scala.collection.mutable.Map.empty[String, KillSwitch]

  def receive = {
    case AddQueue(k, ks) => {
      println(s"Queue runner added ${k}")
      map += ((k, ks))
      sender() ! (())
    }
    case DropQueue(k) => {
      println(s"Queue runner dropped ${k}")
      val maybeItem = map.remove(k)
      maybeItem.map(_.shutdown())
      sender() ! (())
    }
    case ListQueues => {
      sender() ! map.keySet.toList
    }
    case WipeQueues => {
      println(s"Wiping all queues ${map.keySet}")
      map.values.map(_.shutdown())
      map.clear()
      sender() ! (())
    }
    case msg => {
      println("Unknown message: " + msg)
      sender() ! (())
    }
  }
}

@Singleton
class QueueManagementService @Inject() (
  configuration:        play.api.Configuration,
  applicationLifecycle: ApplicationLifecycle,
  wsClient:             WSClient)(implicit mat: akka.stream.Materializer, actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) {

  // TODO: set actual dispatchers
  val queueStateActor = actorSystem.actorOf(
    Props(
      classOf[QueueStateActor]))

  applicationLifecycle.addStopHook { () =>
    wipeQueues()
  }

  private def shutdown(key: String): Future[Unit] = {
    implicit val timeout = Timeout(1.second)
    (queueStateActor ? DropQueue(key)).mapTo[Unit]
  }

  private def add(key: String, killSwitch: KillSwitch): Future[Unit] = {
    implicit val timeout = Timeout(1.second)
    (queueStateActor ? AddQueue(key, killSwitch)).mapTo[Unit]
  }

  def runQueue[T](
    key:         String,
    concurrency: Int,
    source:      Source[T, Any])(
    f: T => Future[Unit])(
    ack: T => Future[Unit]): Future[Unit] = {
    for {
      _ <- shutdown(key)
      (killSwitch, fut) = source
        .viaMat(KillSwitches.single)(Keep.right)
        .mapAsyncUnordered(concurrency) { i =>
          f(i).recover {
            case t: Throwable => {
              println("ERROR with queue item")
              t.printStackTrace()
              i
            }
          }.map(_ => i)
        }
        .mapAsync(1)(ack)
        .toMat(Sink.ignore)(Keep.both).run()
      _ = fut.recover {
        case t: Throwable => {
          println("Queue died")
          println(t)
        }
      }
      _ <- add(key, killSwitch)
    } yield {
      ()
    }
  }

  def listQueues(): Future[List[String]] = {
    implicit val timeout = Timeout(1.second)
    (queueStateActor ? ListQueues).mapTo[List[String]]
  }

  def wipeQueues(): Future[Unit] = {
    implicit val timeout = Timeout(5.seconds)
    (queueStateActor ? WipeQueues).mapTo[Unit]
  }
}
