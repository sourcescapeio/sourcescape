package test

import services._
import models.query._
import models.{ Sinks, IndexType }
import models.graph._
import org.scalatestplus.play.guice._
import scala.concurrent.ExecutionContext
import play.api.libs.json._

trait QueryHelpers {
  self: GuiceOneAppPerSuite =>

  protected def dataForQuery(indexType: IndexType, targeting: QueryTargeting[TraceUnit])(q: String*)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) = {
    val srcLogService = app.injector.instanceOf[SrcLogCompilerService]
    val relationalQueryService = app.injector.instanceOf[RelationalQueryService]

    val query = SrcLogCodeQuery.parseOrDie(q.mkString("\n"), indexType)
    for {
      builderQuery <- srcLogService.compileQuery(query)(targeting)
      result <- relationalQueryService.runQuery(
        builderQuery,
        explain = false,
        progressUpdates = true)(targeting, QueryScroll(None))
      data <- result.source.runWith {
        Sinks.ListAccum[Map[String, JsValue]]
      }
    } yield {
      data
    }
  }

  protected def dataForQueryGeneric(targeting: QueryTargeting[GenericGraphUnit])(q: String*)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) = {
    val srcLogService = app.injector.instanceOf[SrcLogCompilerService]
    val relationalQueryService = app.injector.instanceOf[RelationalQueryService]

    val query = SrcLogGenericQuery.parseOrDie(q.mkString("\n"))
    query.edges.foreach(println)
    for {
      builderQuery <- srcLogService.compileQuery(query)(targeting)
      result <- relationalQueryService.runQueryGenericGraph(
        builderQuery,
        explain = false,
        progressUpdates = false)(targeting, QueryScroll(None))
      data <- result.source.runWith {
        Sinks.ListAccum[Map[String, JsValue]]
      }
    } yield {
      data
    }
  }

  protected def rawESQuery(index: String, query: JsObject)(implicit ex: ExecutionContext) = {
    val elasticSearchService = app.injector.instanceOf[ElasticSearchService]
    for {
      data <- elasticSearchService.search(index, query)
    } yield {
      (data \ "hits" \ "hits").as[List[JsValue]]
    }
  }

  protected def printTable(data: List[Map[String, JsValue]])(columns: ((String, String), GenericGraphNode => String)*) = {
    val indent = " " * 4
    val maxColumn = columns.map {
      case ((_, c), _) => c.length
    }.max

    data.zipWithIndex.foreach {
      case (d, idx) => {
        println(s"Row ${idx + 1}")
        columns.foreach {
          case ((c, label), f) => {
            val fillerIndent = " " * (maxColumn - label.length) + indent

            d.get(c) match {
              case Some(n) => {
                val node = n \ "terminus" \ "node"
                val value = f(node.as[GenericGraphNode])
                println(indent + label + ":" + fillerIndent + value)
              }
              case None => {
                println(indent + label + ":" + fillerIndent + "NULL")
              }
            }
          }
        }
      }
    }
  }
}