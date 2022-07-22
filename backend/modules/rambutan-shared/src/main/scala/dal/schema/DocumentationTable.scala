package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait DocumentationTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class DocumentationTable(tag: Tag) extends Table[Documentation](tag, "documentation") with SafeIndex[Documentation] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def parentId = column[Option[Int]]("parent_id")

    def * = (id, orgId, parentId) <> (Documentation.tupled, Documentation.unapply)
  }

  object DocumentationTable extends SlickIdDataService[DocumentationTable, Documentation](TableQuery[DocumentationTable]) {
    // object bySchema extends Lookup[Int, List](_.schemaId)(_.schemaId)
    // object byIndex extends Lookup[Int, List](_.indexId)(_.indexId)

    // object byPK extends CompositeLookup2[Int, Int, Option](bySchema, byIndex)
  }
}
