package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import java.util.Base64
import akka.stream.scaladsl.Source

@Singleton
class ElasticSearchService @Inject() (
  configuration: play.api.Configuration,
  wsClient:      WSClient)(implicit actorSystem: akka.actor.ActorSystem, ec: ExecutionContext) extends ElasticSearchClient(
  configuration.get[String]("elasticsearch.host"),
  configuration.get[String]("elasticsearch.port"),
  wsClient)(ec)

class ElasticSearchClient(
  ElasticSearchHost: String,
  ElasticSearchPort: String,
  wsClient:          WSClient
// metricsService: MetricsService
)(implicit ec: ExecutionContext) {

  private def esRequest(url: String) = {
    wsClient.url(s"http://${ElasticSearchHost}:${ElasticSearchPort}/${url}")
  }

  /**
   * Admin
   */
  def ping(): Future[JsValue] = {
    for {
      resp <- esRequest("_nodes/_master").get()
    } yield {
      resp.json
    }
  }

  /**
   * Index admin
   */

  def getIndices(): Future[List[JsObject]] = {
    for {
      resp <- esRequest("_cat/indices?format=json").get()
    } yield {
      resp.json.as[List[JsObject]]
    }
  }

  def ensureIndex(indexName: String, mappings: JsObject) = {
    for {
      existing <- getIndex(indexName)
      _ <- existing match {
        case Some(_) => Future.successful(())
        case _       => createIndex(indexName, mappings)
      }
    } yield {
      ()
    }
  }

  def createIndex(indexName: String, mappings: JsObject) = {
    for {
      resp <- esRequest(indexName).put(mappings)
      _ = if (resp.status =/= 200) {
        println(resp.json)
        throw Errors.requestError(resp.json)
      }
    } yield {
      resp.json
    }
  }

  def dropIndex(indexName: String) = {
    for {
      resp <- esRequest(indexName).delete()
    } yield {
      resp.json
    }
  }

  def getIndex(indexName: String): Future[Option[JsObject]] = {
    for {
      resp <- esRequest(s"_cat/indices/${indexName}?format=json").get()
      res = if (resp.status =?= 200) {
        resp.json.as[List[JsObject]].headOption
      } else if (resp.status =?= 404) {
        None
      } else {
        throw Errors.requestError(resp.json)
      }
    } yield {
      res
    }
  }

  /**
   * Indexing
   */
  def index(indexName: String, blob: JsObject): Future[JsValue] = {
    for {
      resp <- esRequest(s"${indexName}/_doc").post(blob)
    } yield {
      resp.json
    }
  }

  def indexBulk(indexName: String, blobs: Seq[JsValue]): Future[Unit] = {
    indexBulkWithId(indexName, blobs.map(None -> _), false)
  }

  def indexBulkWithId(indexName: String, blobs: Seq[(Option[String], JsValue)], waitFor: Boolean): Future[Unit] = {
    val body = akka.stream.scaladsl.Source(blobs).map {
      case (maybeKey, blob) => {
        List(
          Json.obj("index" -> Json.obj("_id" -> maybeKey)),
          blob)
      }
    }.mapConcat(i => i).map { item =>
      // println(item)
      akka.util.ByteString.apply(Json.stringify(item) + "\n")
    }

    val maybeRefresh = withFlag(waitFor) {
      "?refresh=wait_for"
    }

    for {
      resp <- esRequest(s"${indexName}/_bulk" + maybeRefresh).addHttpHeaders("Content-Type" -> "application/json").withBody(body).execute("POST")
    } yield {
      val respJson = (resp.json \ "items").asOpt[List[JsObject]].getOrElse(Nil)
      val errors = respJson.flatMap { json =>
        val base = (json \ "index")
        (base \ "error").asOpt[JsObject] match {
          case Some(_) => base.asOpt[JsObject]
          case _       => None
        }
      }
      if (errors.length > 0) {
        errors.foreach(println)
        throw new Exception(s"Error while indexing ${indexName} " + errors)
      }
      ()
    }
  }

  def delete(indexName: String, query: JsObject): Future[JsValue] = {
    for {
      resp <- esRequest(s"${indexName}/_delete_by_query").post(Json.obj(
        "query" -> query))
    } yield {
      println(resp.json)
      resp.json
    }
  }

  /**
   * Search
   */

  def count(indexName: String, query: JsObject): Future[JsValue] = {
    for {
      resp <- esRequest(s"${indexName}/_count").withBody(
        Json.obj(
          "query" -> query)).get()
    } yield {
      resp.json
    }
  }

  def refresh(indexName: String): Future[Unit] = {
    for {
      initial <- esRequest(s"${indexName}/_refresh").post(Json.obj())
    } yield {
      ()
    }
  }

  // Sort is necessary
  def source(indexName: String, query: JsObject, sort: List[(String, String)], scrollSize: Int, additional: JsObject = Json.obj()): Future[(Long, Source[JsObject, _])] = {
    val baseQuery = additional ++ Json.obj(
      "size" -> scrollSize,
      "query" -> query,
      "sort" -> sort.map {
        case (k, d) => Json.obj(k -> d)
      })

    def getSearchAfter(item: Option[JsObject]): List[String] = {
      item.toList.flatMap { i =>
        sort.map {
          case (k, _) => (i \ "_source" \ k).as[String]
        }
      }
    }

    // metricsService.incInitialCounter()

    // val initialTimer = metricsService.getInitialTimer()

    for {
      initial <- esRequest(s"${indexName}/_search").post(baseQuery)
      _ = if (initial.status =/= 200) {
        throw Errors.requestError(initial.json)
      }
      initialJson = initial.json
      total = (initialJson \ "hits" \ "total" \ "value").as[Long]
      initialHits = (initialJson \ "hits" \ "hits").as[List[JsObject]]
      initialSearchAfter = getSearchAfter(initialHits.lastOption)
    } yield {
      // initialTimer.observeDuration()

      val source = Source.repeat(1).scanAsync((initialSearchAfter, initialHits)) {
        case ((searchAfter, _), _) if searchAfter.length =?= sort.length => {
          // metricsService.incCounter()
          // val timer = metricsService.getTimer()
          for {
            next <- esRequest(s"${indexName}/_search").post(baseQuery ++ Json.obj(
              "search_after" -> searchAfter))
            _ = if (next.status =/= 200) {
              println(next.json)
              throw Errors.requestError(next.json)
            }
            nextJson = next.json
            nextHits = (nextJson \ "hits" \ "hits").as[List[JsObject]]
            nextSearchAfter = getSearchAfter(nextHits.lastOption)
          } yield {
            // timer.observeDuration()
            (nextSearchAfter, nextHits)
          }
        }
        case _ => Future.successful((Nil, Nil))
      }.takeWhile(_._2.nonEmpty).mapConcat {
        case (_, items) => items
      }

      (total, source)
    }
  }

  def search(indexName: String, query: JsObject, additional: JsObject = Json.obj()): Future[JsValue] = {
    for {
      resp <- esRequest(s"${indexName}/_search").post(additional ++ Json.obj(
        "query" -> query))
      _ = if (resp.status =/= 200) {
        throw Errors.requestError(resp.json)
      }
    } yield {
      resp.json
    }
  }

  def get(indexName: String, id: String): Future[Option[JsValue]] = {
    for {
      resp <- esRequest(s"${indexName}/_doc/${id}").get
    } yield {
      resp.status match {
        case 200 => Some(resp.json)
        case 404 => None
        case _   => throw Errors.requestError(resp.json)
      }
    }
  }
}