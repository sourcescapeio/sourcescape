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
import akka.stream.scaladsl.{ Source, Sink }

@Singleton
class CronService @Inject() (
  configuration: play.api.Configuration,
  redisService:  RedisService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  def source[T](tick: FiniteDuration)(f: => Future[List[T]]): Source[T, Any] = {
    Source.tick(tick, tick, ()).mapAsync(1) { _ =>
      // Assumption: objects is small (< 100)
      f
    }.mapConcat(i => i)
  }

  def sourceFlatMap[T](tick: FiniteDuration)(f: => Source[T, Any]): Source[T, Any] = {
    Source.tick(tick, tick, ()).flatMapConcat((_) => f)
  }
}
