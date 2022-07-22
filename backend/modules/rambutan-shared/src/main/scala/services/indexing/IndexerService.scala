package services

import models._
import javax.inject._
import models.index.{ GraphEdge, GraphResult }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow, Sink, Keep, GraphDSL, Merge, Broadcast, FileIO }
import akka.stream.OverflowStrategy

import akka.util.ByteString
import org.joda.time._
import models.graph._

@Singleton
class IndexerService @Inject() (
  configuration:        play.api.Configuration,
  elasticSearchService: ElasticSearchService,
  logService:           LogService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  def reportProgress[T](total: Int)(report: Int => Any) = {
    Flow[T].statefulMapConcat { () =>
      val allFiles = collection.mutable.Set.empty[T]
      var previousReport = new DateTime().getMillis() - 1000
      var previousProgress = 0

      {
        case file => {
          allFiles.add(file)
          // async is fine
          val current = new DateTime().getMillis()
          if ((current - previousReport) > 1000) {
            previousReport = current
            val progress = (allFiles.size / total.toDouble * 100).toInt
            if (progress > previousProgress) {
              report(progress)
              previousProgress = progress
            }
          }
          file :: Nil
        }
      }
    }
  }

  def fanoutIndexing[T](items: Flow[T, (String, List[(Option[String], JsValue)]), Any]*) = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val bcast = builder.add(Broadcast[T](items.length))
      val merge = builder.add(Merge[(String, List[(Option[String], JsValue)])](items.length))

      items.foreach { f =>
        bcast ~> f ~> merge
      }

      akka.stream.FlowShape(bcast.in, merge.out)
    })
  }

  def writeElasticSearch(concurrency: Int, waitFor: Boolean = false)(record: WorkRecord) = {
    Flow[(String, List[(Option[String], JsValue)])].mapConcat {
      case (idx, documents) => documents.map(doc => idx -> doc)
    }.groupBy(concurrency, _._1).groupedWithin(2000, 1.second).map { items =>
      val idx = items.head._1
      (idx, items.map(_._2))
    }.mergeSubstreamsWithParallelism(concurrency).mapAsyncUnordered(concurrency) {
      case (esIndex, documents) => {
        logService.withRecord(record) { implicit record =>
          for {
            _ <- elasticSearchService.indexBulkWithId(esIndex, documents, waitFor)
            _ <- logService.event(s"Indexed ${documents.length} documents to ${esIndex}")
          } yield {
            ()
          }
        }
      }
    }.mapConcat(_.toList)
  }

  /**
   * For generics
   */
  private def graphFlow[T](index: String)(f: ExpressionWrapper[_ <: GenericNodeBuilder] => List[(Option[String], JsValue)]) = {
    Flow[ExpressionWrapper[_ <: GenericNodeBuilder]]
      .mapConcat(f)
      .groupedWithin(1000, 2.seconds)
      .map(index -> _.toList)
  }

  def wrapperFlow(orgId: Int, record: WorkRecord) = {
    Flow[ExpressionWrapper[GenericNodeBuilder]]
      .via(fanoutIndexing(
        graphFlow(GenericGraphNode.globalIndex) {
          _.allNodes.map(n => n.json(orgId))
        },
        graphFlow(GenericGraphEdge.globalIndex) {
          _.allEdges.map(e => e.json(orgId))
        }))
      .via(writeElasticSearch(concurrency = 2, waitFor = true)(record))
  }

  def writeWrapper(orgId: Int, wrapper: ExpressionWrapper[GenericNodeBuilder])(record: WorkRecord): Future[Unit] = {
    Source(wrapper :: Nil)
      .via(wrapperFlow(orgId, record))
      .runWith(Sink.ignore)
      .map(_ => ())
  }
}
