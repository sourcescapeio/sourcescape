package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait AnnotationColumnTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class AnnotationColumnTable(tag: Tag) extends Table[AnnotationColumn](tag, "annotation_column") with SafeIndex[AnnotationColumn] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def schemaId = column[Int]("schema_id")
    def name = column[String]("name")
    def columnType = column[AnnotationColumnType]("column_type")

    def * = (id, schemaId, name, columnType) <> (AnnotationColumn.tupled, AnnotationColumn.unapply)
  }

  object AnnotationColumnTable extends SlickIdDataService[AnnotationColumnTable, AnnotationColumn](TableQuery[AnnotationColumnTable]) {
    object bySchema extends Lookup[Int, List](_.schemaId)(_.schemaId)
  }
}
