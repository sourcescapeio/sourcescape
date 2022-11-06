package test

import models.index._
import models._
import models.query._
import models.graph._
import services._
import workers._

import silvousplay.imports._

import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import sangria.macros._
import org.scalatest.Tag

import play.api.test._
import play.api.test.Helpers._
import play.api.inject.guice._
import play.api.inject.bind
import play.api.libs.json._

import akka.stream.scaladsl.{ Source, Sink }
import akka.util.ByteString

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

import javax.inject._

import akka.http.scaladsl.model.ws._

abstract class RealGitSpec extends RambutanSpec {
  // to override. not using val cuz we want to force laziness for testcontainers
  def config(): Map[String, Any]

  override def fakeApplication() = {
    val renderedConfig = config().toSeq

    new GuiceApplicationBuilder()
      .configure(
        "application.router" -> "api.Routes",
        "use.watcher" -> false // should already be set, but just to be sure
      ).configure(
          renderedConfig: _*)
      .build()
  }

  "Scanning directories" should {
    // sbt "project rambutanTest" "testOnly test.RealGitSpecCompose -- -z work"
    "work" in {

      println("HELLO")

      val gitService = app.injector.instanceOf[LocalGitService]

      val container = await {
        gitService.getGitRepo("/Users/jieren/Projects/sourcescape/sourcescape")
      }

      println(container)
    }
  }
}

class RealGitSpecCompose
  extends RealGitSpec {

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
