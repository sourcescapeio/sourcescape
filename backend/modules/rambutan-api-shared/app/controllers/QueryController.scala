package controllers

import models._
import models.query._
import javax.inject._
import silvousplay.api._
import silvousplay.imports._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.Base64
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Source, Sink, Merge }
import play.api.libs.json._

@Singleton
class QueryController @Inject() (
  configuration:    play.api.Configuration,
  telemetryService: TelemetryService,
  authService:      services.AuthService,
  //
  repoService:            services.RepoService,
  repoDataService:        services.RepoDataService,
  repoIndexDataService:   services.RepoIndexDataService,
  queryTargetingService:  services.QueryTargetingService,
  graphQueryService:      services.GraphQueryService,
  relationalQueryService: services.RelationalQueryService,
  srcLogService:          services.SrcLogCompilerService)(implicit ec: ExecutionContext, as: ActorSystem) extends API with StreamResults {

  def getGrammars() = {
    api { implicit request =>
      IndexType.all.map { it =>
        it.identifier -> it.edgeIndexName
      }
    }
  }

  /**
   * Parsing
   */
  def parseSrcLog(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          val query = SrcLogCodeQuery.parseOrDie(form.q, indexType)
          for {
            targeting <- queryTargetingService.resolveTargeting(orgId, indexType, QueryTargetingRequest.AllLatest(None))
            qs <- srcLogService.compileQueryMultiple(query)(targeting)
          } yield {
            qs.map {
              case (k, v) => k -> QueryString.stringify(v)
            }
          }
        }
      }
    }
  }

  /**
   * Stream endpoints
   */
  private def streamResult(result: RelationalQueryResult) = {
    val tableHeader = result.header
    val progressSource = result.progressSource.map(Left.apply)
    val renderedSource = result.source.map(Right.apply)
    val mergedSource = renderedSource.merge(progressSource).map {
      case Left(progress) => {
        Json.obj(
          "type" -> "progress",
          "progress" -> progress)
      }
      case Right(dto) => {
        Json.obj(
          "type" -> "data",
          "obj" -> dto)
      }
    }

    streamQuery(tableHeader, mergedSource)
  }

  def builderQuery(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      authService.authenticatedReposForOrg(orgId, RepoRole.Pull) { repos =>
        withJson { form: BuilderQueryForm =>
          telemetryService.withTelemetry { implicit c =>
            val repoIds = repos.map(_.repoId)
            val allQueries = SrcLogOperations.extractComponents(form.query.toModel).map(_._2)
            val selectedQuery = allQueries.find(_.vertexes.contains(form.queryKey)).getOrElse {
              throw new Exception(s"invalid query key ${form.queryKey}")
            }

            for {
              targetingRequest <- form.indexIds match {
                case Some(idx) => repoIndexDataService.verifiedIndexIds(idx, repoIds.toSet).map { filteredIds =>
                  QueryTargetingRequest.ForIndexes(filteredIds, form.fileFilter)
                }
                case None => Future.successful {
                  QueryTargetingRequest.RepoLatest(repoIds, form.fileFilter)
                }
              }
              targeting <- queryTargetingService.resolveTargeting(orgId, selectedQuery.language, targetingRequest)
              scroll = QueryScroll(None)
              baseQuery <- srcLogService.compileQuery(selectedQuery)(targeting)
              builderQuery = baseQuery.applyOffsetLimit(form.offset, form.limit)
              result <- relationalQueryService.runQuery(
                builderQuery,
                explain = false,
                progressUpdates = true)(targeting, c, scroll)
            } yield {
              streamResult(result)
            }
          }
        }
      }
    }
  }

  def relationalQuery(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          telemetryService.withTelemetry { implicit c =>
            relationalQueryService.parseQuery(form.q) match {
              case Right((scrollKey, query)) => for {
                targeting <- queryTargetingService.resolveTargeting(
                  orgId,
                  indexType,
                  QueryTargetingRequest.AllLatest(None))
                scroll = QueryScroll(scrollKey)
                result <- relationalQueryService.runQuery(
                  query,
                  explain = true,
                  progressUpdates = true)(targeting, c, scroll)
                tableHeader = Json.obj(
                  "results" -> result.header,
                  "explain" -> result.explain.headers)
                source = result.source
                // main data
                withShutdown = source.map(Right.apply).alsoTo(Sink.onComplete({ _ =>
                  result.completeExplain
                }))
                // explain stream
                explainSource = result.explain.source.getOrElse(Source(Nil)).map(Left.apply).map(Left.apply)
                progressSource = result.progressSource.map(Right.apply).map(Left.apply)
                // merge together
                mergedSource = withShutdown.merge(explainSource).merge(progressSource).map {
                  case Left(Left(explain)) => {
                    Json.obj(
                      "type" -> "explain",
                      "obj" -> Json.toJson(explain))
                  }
                  case Left(Right(progress)) => {
                    Json.obj(
                      "type" -> "progress",
                      "progress" -> progress)
                  }
                  case Right(dto) => {
                    Json.obj(
                      "type" -> "data",
                      "obj" -> dto)
                  }
                }
              } yield {
                streamQuery(tableHeader, mergedSource)
              }
              case Left(fail) => {
                throw Errors.badRequest("query.parse", fail.toString)
              }
            }
          }
        }
      }
    }
  }

  def graphQuery(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          telemetryService.withTelemetry { implicit c =>
            graphQueryService.parseQuery(form.q) match {
              case Right((targetingRequest, query)) => for {
                targeting <- queryTargetingService.resolveTargeting(
                  orgId,
                  indexType,
                  targetingRequest.getOrElse(QueryTargetingRequest.AllLatest(None)))
                (tableHeader, source) <- graphQueryService.runQuery(query)(targeting, c)
              } yield {
                streamQuery(tableHeader, source.map(_.dto))
              }
              case Left(fail) => throw Errors.badRequest("query.parse", fail.toString)
            }
          }
        }
      }
    }
  }

  /**
   * New stuff
   */
  def srcLogQueryExperimental(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          telemetryService.withTelemetry { context =>
            val query = SrcLogCodeQuery.parseOrDie(form.q, indexType)
            for {
              targeting <- queryTargetingService.resolveTargeting(orgId, indexType, QueryTargetingRequest.AllLatest(None))
              relationalQuery <- srcLogService.compileQuery(query)(targeting)
              scroll = QueryScroll(None)
              result <- relationalQueryService.runQuery(
                relationalQuery,
                explain = true,
                progressUpdates = true)(targeting, context, scroll)
              tableHeader = Json.obj(
                "results" -> result.header,
                "explain" -> result.explain.headers)
              source = result.source
              // main data
              withShutdown = source.map(Right.apply).alsoTo(Sink.onComplete({ _ =>
                result.completeExplain
              }))
              // explain stream
              explainSource = result.explain.source.getOrElse(Source(Nil)).map(Left.apply).map(Left.apply)
              progressSource = result.progressSource.map(Right.apply).map(Left.apply)
              // merge together
              mergedSource = withShutdown.merge(explainSource).merge(progressSource).map {
                case Left(Left(explain)) => {
                  Json.obj(
                    "type" -> "explain",
                    "obj" -> Json.toJson(explain))
                }
                case Left(Right(progress)) => {
                  Json.obj(
                    "type" -> "progress",
                    "progress" -> progress)
                }
                case Right(dto) => {
                  Json.obj(
                    "type" -> "data",
                    "obj" -> dto)
                }
              }
            } yield {
              streamQuery(tableHeader, mergedSource)
            }
          }
        }
      }
    }
  }

  def relationalQueryExperimental(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          telemetryService.withTelemetry { context =>
            println(context.traceId)

            relationalQueryService.parseQuery(form.q) match {
              case Right((scrollKey, query)) => for {
                targeting <- context.withSpan("query.relational.targeting-resolution") { _ =>
                  queryTargetingService.resolveTargeting(
                    orgId,
                    indexType,
                    QueryTargetingRequest.AllLatest(None))
                }
                scroll = QueryScroll(scrollKey)
                result <- relationalQueryService.runQuery(
                  query,
                  explain = true,
                  progressUpdates = true)(targeting, context, scroll)
                tableHeader = Json.obj(
                  "results" -> result.header,
                  "explain" -> result.explain.headers)
                source = result.source
                // main data
                withShutdown = source.map(Right.apply).alsoTo(Sink.onComplete({ _ =>
                  result.completeExplain
                }))
                // explain stream
                explainSource = result.explain.source.getOrElse(Source(Nil)).map(Left.apply).map(Left.apply)
                progressSource = result.progressSource.map(Right.apply).map(Left.apply)
                // merge together
                mergedSource = withShutdown.merge(explainSource).merge(progressSource).map {
                  case Left(Left(explain)) => {
                    Json.obj(
                      "type" -> "explain",
                      "obj" -> Json.toJson(explain))
                  }
                  case Left(Right(progress)) => {
                    Json.obj(
                      "type" -> "progress",
                      "progress" -> progress)
                  }
                  case Right(dto) => {
                    Json.obj(
                      "type" -> "data",
                      "obj" -> dto)
                  }
                }
              } yield {
                streamQuery(tableHeader, mergedSource)
              }
              case Left(fail) => {
                throw Errors.badRequest("query.parse", fail.toString)
              }
            }
          }
        }
      }
    }
  }

  def relationalQueryTime(orgId: Int, indexType: IndexType) = {
    api { implicit request =>
      authService.authenticatedForOrg(orgId, OrgRole.Admin) {
        withForm(QueryForm.form) { form =>
          telemetryService.withTelemetry { context =>

            println(context.traceId)

            relationalQueryService.parseQuery(form.q) match {
              case Right((scrollKey, query)) => for {
                targeting <- context.withSpan("query.relational.targeting-resolution") { _ =>
                  queryTargetingService.resolveTargeting(
                    orgId,
                    indexType,
                    QueryTargetingRequest.AllLatest(None))
                }
                scroll = QueryScroll(scrollKey)
                source <- {
                  relationalQueryService.runQuery(
                    query,
                    explain = false,
                    progressUpdates = false)(targeting, context, scroll)
                  // relationalQueryServiceExperimental.runWithoutHydration(
                  //   query,
                  //   explain = false,
                  //   progressUpdates = true)(targeting, cc, scroll)
                }
                _ <- source.source.runWith(Sink.ignore)
              } yield {
                Json.obj(
                  "traceId" -> context.traceId)
              }
              case Left(fail) => {
                throw Errors.badRequest("query.parse", fail.toString)
              }
            }
          }
        }
      }
    }
  }
}
