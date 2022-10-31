package test.unit

import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import models.query._
import akka.stream.scaladsl._
import silvousplay.api._
import services._
import play.api.libs.json._
import models.Sinks
import akka.stream.Materializer
import akka.actor.ActorSystem
import scala.concurrent.Future
import org.mockito.MockitoSugar
import models.IndexType
import models.ESQuery

// sbt "project rambutanTest" "testOnly test.unit.StringifySpec"
class StringifySpec extends PlaySpec with MockitoSugar {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val as = ActorSystem("test")
  implicit val mat = Materializer(as)

  "Stringify" should {

    // sbt "project rambutanTest" "testOnly test.unit.StringifySpec -- -z work"
    "work" in {

      val (_, q) = GraphQuery2.parseOrDie("""
        root {
          type: "class"
        }.linear_traverse [
          ?["javascript::class_property"],
          *["javascript::class_decorator"]
        ].node_check {
          type: "call"
        }
      """)

      println(q)

      println {
        QueryString2.stringifyGraphQuery(q)
      }
    }
  }
}
