package services

import models._
import models.query._
import models.graph._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.libs.json._
import org.joda.time._

@Singleton
class SchemaService @Inject() (
  dao:                   dal.SharedDataAccessLayer,
  configuration:         play.api.Configuration,
  snapshotService:       SnapshotService,
  documentationService:  DocumentationService,
  savedQueryDataService: SavedQueryDataService,
  elasticSearchService:  ElasticSearchService,
  logService:            LogService)(implicit ec: ExecutionContext) {

  def createSchema(
    orgId:         Int,
    title:         String,
    fieldAliases:  Map[String, String],
    context:       SrcLogCodeQueryDTO,
    selected:      List[String],
    named:         List[String],
    fileFilter:    Option[String],
    selectedRepos: List[Int]): Future[SchemaWithQuery] = {
    //
    if (selected.isEmpty) {
      throw Errors.badRequest("invalid.schema", "Need to select at least one column")
    }
    val srcLogQuery = context.toModel

    for {
      savedQuery <- savedQueryDataService.createSavedQuery(
        orgId,
        s"[schema] ${title}",
        srcLogQuery,
        selected.head,
        temporary = false)
      builderTrees = {
        BuilderTree.buildFromQuery(srcLogQuery)
      }
      // NOTE: this is a breadth first search
      sequence = {
        builderTrees.map(_.breadthFirstSearch.distinct).flatten
      }
      schema = Schema(
        0,
        orgId,
        title,
        Json.toJson(fieldAliases),
        savedQuery.id,
        selected,
        named,
        sequence,
        fileFilter,
        selectedRepos)
      schemaId <- dao.SchemaTable.insert(schema)
    } yield {
      SchemaWithQuery(
        schema.copy(id = schemaId),
        savedQuery)
    }
  }

  def listSchemasForOrg(orgId: Int) = {
    dao.SchemaTable.byOrg.lookup(orgId)
  }

  def getSchema(id: Int) = {
    dao.SchemaTable.byId.lookup(id)
  }

  def schemaDetails(id: Int): Future[Option[HydratedSchema]] = {
    dao.SchemaTable.byId.lookup(id).flatMap { maybeSchema =>
      withDefined(maybeSchema) { schema =>
        for {
          latest <- snapshotService.getLatestSnapshot(schema)
          savedQuery <- savedQueryDataService.getSavedQuery(schema.orgId, schema.savedQueryId).map {
            _.getOrElse(throw new Exception("invalid saved query"))
          }
          columns <- documentationService.getAnnotationColumns(schema.id)
        } yield {
          Option {
            HydratedSchema(
              schema,
              savedQuery,
              latest,
              columns)
          }
        }
      }
    }
  }

  def getSnapshotsBatch(schemaId: Int, indexIds: List[Int]): Future[Map[Int, Snapshot]] = {
    val keys = indexIds.map(schemaId -> _)
    dao.SnapshotTable.byPK.lookupBatch(keys).map {
      _.flatMap {
        case ((k1, k2), Some(v)) => Some(k2 -> v)
        case _                   => None
      }
    }
  }

  def clearAllSchemasData(): Future[Unit] = {
    for {
      _ <- elasticSearchService.delete(GenericGraphEdge.globalIndex, ESQuery.termsSearch("type", GenericEdgeType.snapshot.map(_.identifier)))
      _ <- elasticSearchService.delete(GenericGraphNode.globalIndex, ESQuery.termsSearch("type", GenericGraphNodeType.snapshot.map(_.identifier)))
      _ <- dao.SnapshotTable.deleteAll()
    } yield {
      ()
    }
  }
}
