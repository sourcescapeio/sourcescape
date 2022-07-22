package dal

import models._
import silvousplay.data
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait SchemaTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class SchemaTable(tag: Tag) extends Table[Schema](tag, "schema") with SafeIndex[Schema] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def title = column[String]("title")
    def fieldAliases = column[JsValue]("field_aliases")
    def savedQueryId = column[Int]("saved_query_id")
    def selected = column[List[String]]("selected")
    def sequence = column[List[String]]("sequence")
    def named = column[List[String]]("named")
    def fileFilter = column[Option[String]]("file_filter")
    def selectedRepos = column[List[Int]]("selected_repos")

    def * = (id, orgId, title, fieldAliases, savedQueryId, selected, sequence, named, fileFilter, selectedRepos) <> (Schema.tupled, Schema.unapply)
  }

  object SchemaTable extends SlickIdDataService[SchemaTable, Schema](TableQuery[SchemaTable]) {
    object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
    // private object byRepo extends Lookup[String, List](_.repo)(_.repo)
    // private object byPath extends Lookup[String, List](_.path)(_.path)

    // object byUserRepo extends CompositeLookup2[Int, String, List](byUser, byRepo)
    // object byPK extends CompositeLookup3[Int, String, String, Option](byUser, byRepo, byPath)
  }
}
