package services

import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import silvousplay.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.Source
import models.graph._

@Singleton
class SrcLogQueryService @Inject() (
  configuration:          play.api.Configuration,
  srcLogCompilerService:  SrcLogCompilerService,
  relationalQueryService: RelationalQueryService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  def runQueryGeneric(query: SrcLogQuery)(implicit targeting: QueryTargeting[GenericGraphUnit], context: SpanContext): Future[Source[Map[String, GenericGraphNode], Any]] = {
    implicit val queryScroll = QueryScroll(None)
    for {
      relationalQuery <- srcLogCompilerService.compileQuery(query)
      relationalResult <- relationalQueryService.runQueryGenericGraph(relationalQuery, explain = false, progressUpdates = false)
    } yield {
      relationalResult.source.map {
        _.map {
          case (k, v) => k -> (v \ "terminus" \ "node").as[GenericGraphNode]
        }
      }
    }
  }

}
