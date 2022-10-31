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

// sbt "project rambutanTest" "testOnly test.unit.CompilerSpec"
class CompilerSpec extends PlaySpec with MockitoSugar {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val as = ActorSystem("test")
  implicit val mat = Materializer(as)

  "SrcLog Compiler" should {

    // sbt "project rambutanTest" "testOnly test.unit.CompilerSpec -- -z work"
    "work" in {
      val mockES = mock[ElasticSearchService]
      val compilerService = new q1.SrcLogCompilerService(
        mockES)

      val targeting = KeysQueryTargeting(
        IndexType.Javascript,
        Nil,
        Map.empty[Int, List[String]],
        None)

      when(mockES.count(IndexType.Javascript.nodeIndexName, ESQuery.bool(
        filter = ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", Nil),
            ESQuery.termsSearch("type", List("throw")))) :: Nil))).thenReturn(Future.successful(Json.obj(
        "count" -> 10000)))

      when(mockES.count(IndexType.Javascript.nodeIndexName, ESQuery.bool(
        filter = ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", Nil),
            ESQuery.termsSearch("type", List("method")))) :: Nil))).thenReturn(Future.successful(Json.obj(
        "count" -> 500)))

      when(mockES.count(IndexType.Javascript.nodeIndexName, ESQuery.bool(
        filter = ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", Nil),
            ESQuery.termsSearch("type", List("class")))) :: Nil))).thenReturn(Future.successful(Json.obj(
        "count" -> 50)))

      val q = await {
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

      println {
        QueryString2.stringify(q)
      }
    }

    // sbt "project rambutanTest" "testOnly test.unit.CompilerSpec -- -z edge.follows.propagation"
    "edge.follows.propagation" in {
      val mockES = mock[ElasticSearchService]
      val compilerService = new q1.SrcLogCompilerService(
        mockES)

      val targeting = KeysQueryTargeting(
        IndexType.Javascript,
        Nil,
        Map.empty[Int, List[String]],
        None)

      when(mockES.count(IndexType.Javascript.nodeIndexName, ESQuery.bool(
        filter = ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", Nil),
            ESQuery.termsSearch("type", List("instance")))) :: Nil))).thenReturn(Future.successful(Json.obj(
        "count" -> 10)))

      when(mockES.count(IndexType.Javascript.nodeIndexName, ESQuery.bool(
        filter = ESQuery.bool(
          must = List(
            ESQuery.termsSearch("key", Nil),
            ESQuery.termsSearch("type", List("member")))) :: Nil))).thenReturn(Future.successful(Json.obj(
        "count" -> 1)))

      val q = await {
        compilerService.compileQuery(
          SrcLogCodeQuery.parseOrDie(
            """
            javascript::instance_of(A, B).
            javascript::member(B, C).
            """,
          IndexType.Javascript))(targeting)
      }

      println(q)

      println {
        QueryString2.stringify(q)
      }
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
