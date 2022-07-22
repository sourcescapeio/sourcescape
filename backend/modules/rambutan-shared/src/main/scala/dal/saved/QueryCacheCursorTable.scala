package dal

import models._
import silvousplay.imports._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait QueryCacheCursorTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class QueryCacheCursorTable(tag: Tag) extends Table[QueryCacheCursor](tag, "query_cache_cursor") with SafeIndex[QueryCacheCursor] {
    def cacheId = column[Int]("cache_id")
    //
    def key = column[String]("key")
    def start = column[Int]("start")
    def end = column[Int]("end")
    //
    def cursor = column[JsValue]("cursor")

    def pk = withSafePK(s => primaryKey(s, (cacheId, key, start)))

    def * = (cacheId, key, start, end, cursor) <> (QueryCacheCursor.tupled, QueryCacheCursor.unapply)
  }

  object QueryCacheCursorTable extends SlickDataService[QueryCacheCursorTable, QueryCacheCursor](TableQuery[QueryCacheCursorTable]) {
    // object byOrg extends Lookup[Int, List](_.orgId)(_.orgId)

    object byCache extends Lookup[Int, List](_.cacheId)(_.cacheId)
    private object byKey extends Lookup[String, List](_.key)(_.key) // danger, should get index
    private object byStart extends Lookup[Int, List](_.start)(_.start)

    object byCacheKey extends CompositeLookup2[Int, String, List](byCache, byKey) {
      def maxKey(cacheId: Int, key: String, index: Int)(implicit ec: ExecutionContext) = {
        db.run {
          for {
            max <- query((cacheId, key)).filter(_.start <= index).map(_.start).max.result
            _ = println("MAX", max)
            res <- max match {
              case Some(m) => byCacheKeyStart.lookupQuery((cacheId, key, m)).map(_.headOption)
              case None    => DBIO.successful(None)
            }
          } yield {
            res
          }
        }
      }
    }

    object byCacheKeyStart extends CompositeLookup3[Int, String, Int, Option](byCache, byKey, byStart)
  }
}
