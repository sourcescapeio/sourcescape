package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait QueryCacheTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class QueryCacheTable(tag: Tag) extends Table[QueryCache](tag, "query_cache") with SafeIndex[QueryCache] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orgId = column[Int]("org_id")
    // query
    def queryId = column[Int]("query_id") // links to SavedQuery
    def forceRoot = column[String]("force_root")
    def ordering = column[List[String]]("ordering")
    // targeting
    def fileFilter = column[Option[String]]("file_filter")

    def * = (id, orgId, queryId, forceRoot, ordering, fileFilter) <> (QueryCache.tupled, QueryCache.unapply)
  }

  object QueryCacheTable extends SlickIdDataService[QueryCacheTable, QueryCache](TableQuery[QueryCacheTable]) {
    private object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)
    private object byQuery extends Lookup[Int, List](_.queryId)(_.queryId)

    object byOrgQuery extends CompositeLookup2[Int, Int, List](byOrg, byQuery)
  }
}
