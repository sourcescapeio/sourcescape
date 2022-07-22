package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

trait WorkRecordTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class WorkRecordTable(tag: Tag) extends Table[WorkRecord](tag, "work_record") with SafeIndex[WorkRecord] {
    def id = column[String]("id", O.PrimaryKey)
    def parents = column[List[String]]("parents")
    def tags = column[JsValue]("tags")
    def orgId = column[Int]("org_id")
    def status = column[WorkStatus]("status")
    def started = column[Option[Long]]("started")
    def finished = column[Option[Long]]("finished")

    def idx = withSafeIndex("parents")(s => index(s, (orgId, parents)))

    def * = (id, parents, tags, orgId, status, started, finished) <> (WorkRecord.tupled, WorkRecord.unapply)
  }

  object WorkRecordTable extends SlickDataService[WorkRecordTable, WorkRecord](TableQuery[WorkRecordTable]) {
    object byId extends Lookup[String, Option](_.id)(_.id)
    object byParent {
      def lookupQuery(orgId: Int, parentId: String) = {
        table.filter { i =>
          i.orgId === orgId && parentId.bind === i.parents.any
        }
      }

      def lookupQueryBatch(orgId: Int, parentIds: List[String]) = {
        table.filter { i =>
          i.orgId === orgId && i.parents @& parentIds // overlap parents array and parentIds is equiv to ANY CONTAINS
        }
      }

      def deleteQuery(orgId: Int, parentId: String) = {
        lookupQuery(orgId, parentId).delete
      }

      def lookup(orgId: Int, parentId: String)(implicit ec: ExecutionContext) = {
        db.run {
          lookupQuery(orgId, parentId).result
        } map (_.toList)
      }

      def lookupBatch(orgId: Int, parentIds: List[String])(implicit ec: ExecutionContext) = {
        db.run {
          lookupQueryBatch(orgId, parentIds).result
        } map (_.toList)
      }
    }

    object byOrg {
      def lookupTopLevel(orgId: Int)(implicit ec: ExecutionContext) = {
        db.run {
          table.filter { i =>
            i.orgId === orgId && i.parents.length(1) === 0
          }.result
        }
      }
    }

    object updateStatusStartedById {
      private def queryBatch(ids: List[String]) = {
        table.filter { i =>
          i.started.isEmpty && (i.id inSetBind ids)
        }
      }

      def updateBatch(ids: List[String], status: WorkStatus, started: Long) = {
        db.run {
          queryBatch(ids).map { i =>
            (i.status, i.started)
          }.update((status, Some(started)))
        }
      }
    }

    object updateStatusFinishedById {
      private def queryBatch(ids: List[String]) = {
        table.filter { i =>
          i.finished.isEmpty && (i.id inSetBind ids)
        }
      }

      def update(id: String, status: WorkStatus, finished: Long) = updateBatch(List(id), status, finished)

      def updateBatch(ids: List[String], status: WorkStatus, finished: Long) = {
        db.run {
          updateQuery(ids, status, finished)
        }
      }

      def updateQuery(ids: List[String], status: WorkStatus, finished: Long) = {
        queryBatch(ids).map { i =>
          (i.status, i.finished)
        }.update((status, Some(finished)))
      }
    }
  }
}
