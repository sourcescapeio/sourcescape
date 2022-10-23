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

// sbt "project rambutanTest" "testOnly test.unit.CompilerSpec"
class CompilerSpec extends PlaySpec with MockitoSugar {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  //   private def mergeJoin[K, V1, V2](source1: Source[(K, V1), _], source2: Source[(K, V2), _], leftOuter: Boolean = false, rightOuter: Boolean = false)(implicit ordering: Ordering[K], writes: Writes[K]) = {
  //     Source.fromGraph(GraphDSL.create() { implicit builder =>
  //       import GraphDSL.Implicits._
  //       val context = NoopSpanContext
  //       val joiner = builder.add(new MergeJoin[K, V1, V2](context, doExplain = true, leftOuter, rightOuter = rightOuter))

  //       source1 ~> joiner.in0
  //       source2 ~> joiner.in1

  //       akka.stream.SourceShape(joiner.out)
  //     })
  //   }

  //   private def withMaterializer[T](f: Materializer => T) = {
  //     val as = ActorSystem("test")
  //     val mat = Materializer(as)

  //     f(mat)
  //     await(as.terminate())
  //   }

  "SrcLog Compiler" should {

    // sbt "project rambutanTest" "testOnly test.unit.CompilerSpec -- -z work"
    "work" in {
      val as = ActorSystem("test")
      implicit val mat = Materializer(as)
      val mockES = mock[ElasticSearchService]
      val compilerService = new q1.SrcLogCompilerService(
        mockES)

      val targeting = KeysQueryTargeting(
        IndexType.Javascript,
        Nil,
        Map.empty[Int, List[String]],
        None)

      await {
        compilerService.compileQuery(
          SrcLogCodeQuery.parseOrDie(
            """
            javascript::class(CLASS).
            javascript::class_method(CLASS, METHOD).

            javascript::throw(THROW).
            javascript::contains(METHOD, THROW).
            """,
            IndexType.Javascript))(targeting)
      }

      // await {
      //   compilerService.compileQuery(
      //     SrcLogCodeQuery.parseOrDie(
      //       """
      //       javascript::class_decorator(CLASS, CLASSDECORATOR).
      //       javascript::require(NEST)[name="@nestjs/common"].
      //       javascript::member(NEST, NESTCONTROLLER)[name="Controller"].
      //       javascript::call(NESTCONTROLLER, CLASSDECORATOR).

      //       javascript::member(NEST, NESTPOST)[name="Post"].
      //       javascript::class_method(CLASS, CLASSMETHOD).
      //       javascript::method_decorator(CLASSMETHOD, METHODDECORATOR).
      //       javascript::call(NESTPOST, METHODDECORATOR).
      //       """,
      //       IndexType.Javascript,
      //     )
      //   )(targeting)
      // }
    }
  }
}
