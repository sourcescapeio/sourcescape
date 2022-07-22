package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait AnnotationTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class AnnotationTable(tag: Tag) extends Table[Annotation](tag, "annotation") with SafeIndex[Annotation] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def colId = column[Int]("col_id")
    def indexId = column[Int]("index_id")
    def rowKey = column[String]("row_key")
    def payload = column[JsValue]("payload")

    def * = (id, colId, indexId, rowKey, payload) <> (Annotation.tupled, Annotation.unapply)
  }

  object AnnotationTable extends SlickIdDataService[AnnotationTable, Annotation](TableQuery[AnnotationTable]) {
    // object bySchema extends Lookup[Int, List](_.schemaId)(_.schemaId)
    // private object byRepo extends Lookup[String, List](_.repo)(_.repo)
    // private object byPath extends Lookup[String, List](_.path)(_.path)

    // object byUserRepo extends CompositeLookup2[Int, String, List](byUser, byRepo)
    // object byPK extends CompositeLookup3[Int, String, String, Option](byUser, byRepo, byPath)
  }
}
