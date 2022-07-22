package services

import models._
import models.graph._
import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.libs.json._
import org.joda.time._
import akka.stream.scaladsl.{ Source, Sink }

@Singleton
class DocumentationService @Inject() (
  dao:           dal.SharedDataAccessLayer,
  configuration: play.api.Configuration,
  // snapshotterQueueService: SnapshotterQueueService,
  // srcLogQueryService:      SrcLogQueryService,
  // repoIndexDataService:    RepoIndexDataService,
  indexerService: IndexerService,
  logService:     LogService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  def addAnnotationColumn(schemaId: Int, name: String, columnType: AnnotationColumnType): Future[AnnotationColumn] = {
    val obj = AnnotationColumn(0, schemaId, name, columnType)
    for {
      annotId <- dao.AnnotationColumnTable.insert(obj)
    } yield {
      obj.copy(id = annotId)
    }
  }

  def deleteAnnotationColumn(schemaId: Int, columnId: Int): Future[Unit] = {
    dao.AnnotationColumnTable.byId.delete(columnId) map (_ => ())
  }

  def getAnnotationColumns(schemaId: Int): Future[List[AnnotationColumn]] = {
    dao.AnnotationColumnTable.bySchema.lookup(schemaId).map(_.sortBy(_.id))
  }

  def getAnnotationColumn(id: Int): Future[Option[AnnotationColumn]] = {
    dao.AnnotationColumnTable.byId.lookup(id)
  }

  def createDocumentation(orgId: Int, text: String) = {
    val obj = Documentation(0, orgId, None)
    for {
      documentationId <- dao.DocumentationTable.insert(obj)
      blockObj = DocumentationBlock(0, orgId, documentationId, text)
      _ <- dao.DocumentationBlockTable.insert(blockObj)
    } yield {
      obj.copy(id = documentationId)
    }
  }

  def getHydratedDocumentationBatch(documentationIds: List[Int]) = {
    ifNonEmpty(documentationIds) {
      for {
        docs <- dao.DocumentationTable.byId.lookupBatch(documentationIds).map(_.values.flatten)
        blocks <- dao.DocumentationBlockTable.byParent.lookupBatch(documentationIds)
      } yield {
        docs.map { d =>
          d.id -> HydratedDocumentation(
            d,
            blocks.getOrElse(d.id, Nil))
        }.toMap
      }
    }
  }

  def createAnnotation(orgId: Int, annotationColumn: AnnotationColumn, indexId: Int, rowKey: String, payload: JsValue) = {
    // annotationColumn.validate(payload)
    val obj = Annotation(0, annotationColumn.id, indexId, rowKey, payload)

    for {
      annotId <- dao.AnnotationTable.insert(obj)
      annotation = obj.copy(id = annotId)
      wrapper = snapshot.AnnotationWriter.materializeAnnotation(annotationColumn, annotation)
      _ = println(wrapper)
      parent <- logService.createParent(orgId, Json.obj()) // rm this
      _ <- indexerService.writeWrapper(orgId, wrapper)(parent)
    } yield {
      annotation
    }
  }

}
