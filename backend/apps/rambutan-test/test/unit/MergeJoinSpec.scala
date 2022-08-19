package test.unit

import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import models.query._
import akka.stream.scaladsl._
import silvousplay.api._
import services.q9
import play.api.libs.json._
import models.Sinks
import akka.stream.Materializer
import akka.actor.ActorSystem
import scala.concurrent.Future

// sbt "project rambutanTest" "testOnly test.unit.MergeJoinSpec"
class MergeJoinSpec extends PlaySpec with Telemetry {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  private def mergeJoin[K, V1, V2](source1: Source[(K, V1), _], source2: Source[(K, V2), _])(implicit ordering: Ordering[K], writes: Writes[K]) = {
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val context = getSpanContext()
      val joiner = builder.add(new q9.MergeJoin[K, V1, V2](context, doExplain = true, leftOuter = false, rightOuter = false))

      source1 ~> joiner.in0
      source2 ~> joiner.in1

      akka.stream.SourceShape(joiner.out)
    })
  }

  private def withMaterializer[T](f: Materializer => T) = {
    val as = ActorSystem("test")
    val mat = Materializer(as)

    f(mat)
    await(as.terminate())
  }

  "MergeJoin" should {
    "join.normal" in {
      val source1 = Source(List(("1", 1), ("2", 2), ("3", 3)))
      val source2 = Source(List(("1", 1), ("2", 2), ("3", 3)))

      val as = ActorSystem("test")
      implicit val mat = Materializer(as)

      val res = mergeJoin(source1, source2).runWith(Sinks.ListAccum)

      await(res)
      await(as.terminate())

      res.foreach { item =>
        println(item)
      }
    }

    //sbt "project rambutanTest" "testOnly test.unit.MergeJoinSpec -- -z join.pass"
    "join.pass" in {
      // should pass along properly when one stream terminates
      val source1 = Source(List(("1", 1), ("1", 2), ("1", 3), ("1", 4), ("1", 5)))
      val source2 = Source(List(("1", 1)))

      withMaterializer { implicit m =>
        val res = mergeJoin(source1, source2).runWith(Sinks.ListAccum)
        await(res).foreach { item =>
          println(item)
        }
      }
    }

    //sbt "project rambutanTest" "testOnly test.unit.MergeJoinSpec -- -z join.left"
    "join.left" in {
      // should pass along properly when one stream terminates
      val source1 = Source(List(("1", 1), ("2", 1), ("3", 1), ("4", 1), ("5", 1)))
      val source2 = Source(List(("3", 1)))

      withMaterializer { implicit m =>
        val res = mergeJoin(source1, source2).runWith(Sinks.ListAccum)
        // need to pass in left flag
        await(res).foreach { item =>
          println(item)
        }
      }
    }
  }
}
