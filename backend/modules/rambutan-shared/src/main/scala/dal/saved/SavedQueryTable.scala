package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait SavedQueryTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class SavedQueryTable(tag: Tag) extends Table[SavedQuery](tag, "saved_query") with SafeIndex[SavedQuery] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    def name = column[String]("name")
    def language = column[IndexType]("language")
    def nodes = column[JsValue]("nodes")
    def edges = column[JsValue]("edges")
    def aliases = column[JsValue]("aliases")
    def defaultSelected = column[String]("defaulted_selected")
    def temporary = column[Boolean]("temporary")
    def created = column[Long]("created")

    // def pk = withSafePK(s => primaryKey(s, (shaId, file)))
    // def shaIdx = withSafeIndex("sha")(s => index(s, shaId))

    def * = (id, orgId, name, language, nodes, edges, aliases, defaultSelected, temporary, created) <> (SavedQuery.tupled, SavedQuery.unapply)
  }

  object SavedQueryTable extends SlickIdDataService[SavedQueryTable, SavedQuery](TableQuery[SavedQueryTable]) {
    object byOrg extends Lookup[Int, List](_.orgId)(_.orgId) {
      def lookupNotTemporary(orgId: Int) = {
        db.run {
          query(orgId).filter(_.temporary === false).result
        }
      }
    }

    private object byNodes extends Lookup[JsValue, List](_.nodes)(_.nodes)
    private object byEdges extends Lookup[JsValue, List](_.edges)(_.edges)

    object byOrgNodeEdge extends CompositeLookup3[Int, JsValue, JsValue, List](byOrg, byNodes, byEdges)
  }
}
