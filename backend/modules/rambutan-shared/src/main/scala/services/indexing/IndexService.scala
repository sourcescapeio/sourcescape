package services

import models.{ IndexType, IndexSummary, ESIndexSummary, RepoSHAIndex }
import models.index.{ GraphNode, GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import akka.stream.scaladsl.{ Source, Sink }
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._

@Singleton
class IndexService @Inject() (
  configuration:        play.api.Configuration,
  elasticSearchService: ElasticSearchService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  val EdgeIndex = IndexType.Javascript.edgeIndexName
  val NodeIndex = IndexType.Javascript.nodeIndexName

  def cycleNodesIndex(indexType: IndexType) = {
    for {
      _ <- elasticSearchService.dropIndex(indexType.nodeIndexName)
      resp <- elasticSearchService.createIndex(indexType.nodeIndexName, GraphNode.mappings)
    } yield {
      resp
    }
  }

  def cycleEdgesIndex(indexType: IndexType) = {
    for {
      _ <- elasticSearchService.dropIndex(indexType.edgeIndexName)
      resp <- elasticSearchService.createIndex(indexType.edgeIndexName, GraphEdge.mappings)
    } yield {
      resp
    }
  }

  def getIndexSummary(): Future[List[IndexSummary]] = {
    for {
      esIndices <- elasticSearchService.getIndices().map { items =>
        items.map { js =>
          val name = (js \ "index").as[String]
          name -> ESIndexSummary(
            name,
            (js \ "health").as[String],
            (js \ "store.size").as[String],
            (js \ "docs.count").as[String])
        }.toMap
      }
    } yield {
      IndexType.all.map { i =>
        IndexSummary(
          i,
          esIndices.get(i.nodeIndexName),
          esIndices.get(i.edgeIndexName))
      }.toList
    }
  }

  def getNodesIndex(indexType: IndexType) = {
    elasticSearchService.getIndex(indexType.nodeIndexName)
  }

  def getEdgesIndex(indexType: IndexType) = {
    elasticSearchService.getIndex(indexType.edgeIndexName)
  }

  //
  def deleteKey(index: RepoSHAIndex): Future[Unit] = {
    val key = index.esKey

    Source(IndexType.all).mapAsync(1) { indexType =>
      for {
        _ <- elasticSearchService.delete(indexType.nodeIndexName, Json.obj("match" -> Json.obj("key" -> key)))
        _ <- elasticSearchService.delete(indexType.edgeIndexName, Json.obj("match" -> Json.obj("key" -> key)))
      } yield {
        ()
      }
    }.runWith(Sink.ignore) map (_ => ())
  }
}
