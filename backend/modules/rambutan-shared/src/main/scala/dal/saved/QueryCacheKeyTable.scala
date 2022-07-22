package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._
import akka.stream.scaladsl.Source

trait QueryCacheKeyTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class QueryCacheKeyTable(tag: Tag) extends Table[QueryCacheKey](tag, "query_cache_key") with SafeIndex[QueryCacheKey] {
    // def orgId = column[Int]("org_id")
    // def queryId = column[Int]("query_id")
    def cacheId = column[Int]("cache_id")
    def key = column[String]("key")
    def count = column[Int]("count")
    def deleted = column[Boolean]("deleted")
    def lastModified = column[Long]("last_modified")

    def pk = withSafePK(s => primaryKey(s, (cacheId, key)))

    //startIndex,
    def * = (cacheId, key, count, deleted, lastModified) <> (QueryCacheKey.tupled, QueryCacheKey.unapply)
  }

  object QueryCacheKeyTable extends SlickDataService[QueryCacheKeyTable, QueryCacheKey](TableQuery[QueryCacheKeyTable]) {
    object byCache extends Lookup[Int, List](_.cacheId)(_.cacheId)
    private object byKey extends Lookup[String, List](_.key)(_.key)

    object byCacheKey extends CompositeLookup2[Int, String, List](byCache, byKey)

    object Streams {
      def lastModifiedLessThan(range: Long) = {
        Source.fromPublisher {
          db.stream {
            table.filter(i => i.lastModified < range && i.deleted === false).result
          }
        }
      }

      def deleted = {
        Source.fromPublisher {
          db.stream {
            table.filter(_.deleted === true).result
          }
        }
      }
    }

    object updateLastModifiedByCacheKey extends Updater(byCacheKey)(_.lastModified)
    object updateDeletedByCacheKey extends Updater(byCacheKey)(_.deleted)
    // object byQueryKey extends CompositeLookup2[Int, String, List](byQuery, byKey)
  }
}
