package controllers

import javax.inject._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Source, Flow, Sink }
import silvousplay.imports._
import silvousplay.api._
import play.api.mvc.WebSocket
import play.api.libs.json._
import models._

@Singleton
class SchemasController @Inject() (
  val configuration:    play.api.Configuration,
  telemetryService:     TelemetryService,
  socketService:        services.SocketService,
  authService:          services.AuthService,
  schemaService:        services.SchemaService,
  snapshotService:      services.SnapshotService,
  documentationService: services.DocumentationService,
  repoIndexDataService: services.RepoIndexDataService)(implicit ec: ExecutionContext, as: akka.actor.ActorSystem) extends API with StreamResults {

  def createSchema(orgId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      withJson { form: SchemaForm =>
        authService.authenticatedSuperUser {
          schemaService.createSchema(
            orgId,
            form.title,
            form.fieldAliases,
            form.context,
            form.selected,
            form.named,
            form.fileFilter,
            form.selectedRepos) map (_.schema.dto)
        }
      }
    }
  }

  def listSchemas(orgId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        schemaService.listSchemasForOrg(orgId).map(_.map(_.dto))
      }
    }
  }

  def schemaDetails(orgId: Int, schemaId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        telemetryService.withTelemetry { implicit c =>
          schemaService.schemaDetails(schemaId).map(_.map(_.dto))
        }
      }
    }
  }

  def addAnnotationColumn(orgId: Int, schemaId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        documentationService.addAnnotationColumn(schemaId, "Test", AnnotationColumnType.Documentation) map (_ => ())
      }
    }
  }

  def deleteAnnotationColumn(orgId: Int, schemaId: Int, columnId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        documentationService.deleteAnnotationColumn(schemaId, columnId)
      }
    }
  }

  def createAnnotation(orgId: Int, schemaId: Int, columnId: Int) = {
    api(parse.tolerantJson) { implicit request =>
      withJson { form: AnnotationForm =>
        authService.authenticatedSuperUser {
          for {
            doc <- documentationService.createDocumentation(orgId, "Doc")
            column <- documentationService.getAnnotationColumn(columnId).map {
              _.getOrElse(throw new Exception("invalid column"))
            }
            _ <- documentationService.createAnnotation(orgId, column, form.indexId, form.rowKey, Json.obj(
              "type" -> "documentation",
              "id" -> doc.id,
              "link" -> "test"))
          } yield {
            ()
          }
        }
      }
    }
  }

  def clearAllSchemasData() = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        schemaService.clearAllSchemasData()
      }
    }
  }

  /**
   * Snapshots
   */
  def createSnapshotForLatest(orgId: Int, schemaId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        for {
          schema <- schemaService.getSchema(schemaId).map {
            _.getOrElse(throw models.Errors.notFound("schema.dne", "Schema not found"))
          }
          // naive scan repo for latest index
          validIndexes <- repoIndexDataService.getLatestSHAIndexForRepos(schema.selectedRepos).map(_.values.toList)
          // find repos that need snapshot
          existingSnapshots <- snapshotService.getSnapshotsBatch(schemaId, validIndexes.map(_.id))
          missingIndexes = validIndexes.flatMap { index =>
            existingSnapshots.get(index.id) match {
              case Some(_) => None
              case None    => Some(index)
            }
          }
          _ <- snapshotService.upsertSnapshots(orgId, schemaId, missingIndexes.map(_.id))
        } yield {
          ()
        }
      }
    }
  }

  def rerunSnapshotForIndex(orgId: Int, schemaId: Int, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        for {
          _ <- snapshotService.upsertSnapshots(orgId, schemaId, List(indexId))
        } yield {
          ()
        }
      }
    }
  }

  def getSnapshotDataForIndex(orgId: Int, schemaId: Int, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        telemetryService.withTelemetry { implicit c =>
          for {
            (header, source) <- snapshotService.getSnapshotDataForIndex(orgId, schemaId, indexId)
          } yield {
            streamQuery(header, source)
          }
        }
      }
    }
  }

  def deleteSnapshotForIndex(orgId: Int, schemaId: Int, indexId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        telemetryService.withTelemetry { implicit c =>
          for {
            _ <- snapshotService.deleteSnapshotForIndex(orgId, schemaId, indexId)
          } yield {
            ()
          }
        }
      }
    }
  }

  def listSnapshots(orgId: Int, schemaId: Int) = {
    api { implicit request =>
      authService.authenticatedSuperUser {
        snapshotService.getSnapshotsForSchema(schemaId).map(_.map(_.dto))
      }
    }
  }
}
