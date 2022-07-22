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

class RedisService @Inject() (
  configuration: play.api.Configuration)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) {

  val RedisHost = configuration.get[String]("redis.host")
  val RedisPort = configuration.get[Int]("redis.port")
  val redisClient = redis.RedisClient(
    host = RedisHost,
    port = RedisPort)

}