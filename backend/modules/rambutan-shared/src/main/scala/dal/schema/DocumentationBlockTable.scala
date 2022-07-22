package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait DocumentationBlockTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class DocumentationBlockTable(tag: Tag) extends Table[DocumentationBlock](tag, "documentation_block") with SafeIndex[DocumentationBlock] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def parentId = column[Int]("parent_id")
    def text = column[String]("text")

    def * = (id, orgId, parentId, text) <> (DocumentationBlock.tupled, DocumentationBlock.unapply)
  }

  object DocumentationBlockTable extends SlickIdDataService[DocumentationBlockTable, DocumentationBlock](TableQuery[DocumentationBlockTable]) {
    // object bySchema extends Lookup[Int, List](_.schemaId)(_.schemaId)
    object byParent extends Lookup[Int, List](_.parentId)(_.parentId)

    // object byPK extends CompositeLookup2[Int, Int, Option](bySchema, byIndex)
  }
}
