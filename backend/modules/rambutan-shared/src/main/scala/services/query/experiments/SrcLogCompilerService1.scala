package services.q1

import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import services.ElasticSearchService

@Singleton
class SrcLogCompilerService @Inject() (
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val SrcLogLimit = 10

  //: Future[RelationalQuery]
  def compileQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]) = {
    Future.successful {
      println("TEST")
    }
    // optimizeQuery(query)
  }

}