package scripts

import play.api.db.evolutions.Evolutions
import play.api.db.Databases
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Source, Sink }
import play.api.libs.ws.ahc.AhcWSClient
import scala.concurrent.Await
import scala.concurrent.duration._
import models._
import models.index._
import models.graph._

object Initializer {
  val esHost = sys.env.get("ELASTICSEARCH_HOST").getOrElse("localhost")

  val pgHost = sys.env.get("POSTGRES_HOST").getOrElse("localhost")

  // POSTGRES_HOST
  private lazy val db = Databases(
    "org.postgresql.Driver",
    s"jdbc:postgresql://${pgHost}:5432/sourcescape?characterEncoding=UTF-8",
    "default",
    Map(
      "user" -> "sourcescape",
      "password" -> "sourcescape"))

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = Materializer.matFromSystem

  val wsClient = {
    AhcWSClient()
  }

  lazy val elasticSearchService = {

    new services.ElasticSearchClient(
      esHost,
      "9200",
      wsClient)(ec)
  }

  def main(args: Array[String]): Unit = {
    println("Running evolutions...")
    Evolutions.applyEvolutions(db)
    println("Evolutions complete")

    println("Ensuring ES indexes...")
    val esWork = for {
      _ <- Source(IndexType.all).mapAsync(1) { it =>
        for {
          _ <- elasticSearchService.ensureIndex(it.nodeIndexName, GraphNode.mappings)
          _ = println(s"Ensured ${it.identifier} node index")
          _ <- elasticSearchService.ensureIndex(it.edgeIndexName, GraphEdge.mappings)
          _ = println(s"Ensured ${it.identifier} edge index")
        } yield {
          ()
        }
      }.runWith(Sink.ignore)
      // static indexes
      _ <- elasticSearchService.ensureIndex(WorkLogDocument.globalIndex, WorkLogDocument.mappings)
      _ = println("Ensured log index")
      _ <- elasticSearchService.ensureIndex(GenericGraphNode.globalIndex, GenericGraphNode.mappings)
      _ <- elasticSearchService.ensureIndex(GenericGraphEdge.globalIndex, GenericGraphEdge.mappings)
      _ = println("Ensured generic graph indexes")
    } yield {
      println("Ensuring ES indexes complete.")
    }

    val allWork = for {
      test <- esWork.recover {
        case e: Exception => println(e)
      }
      _ = wsClient.close()
      _ <- system.terminate()
    } yield {
      println("Initialization complete!")
    }

    Await.result(allWork, 30.seconds)
  }
}
