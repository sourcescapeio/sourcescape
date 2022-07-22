package services

import models._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import akka.stream.scaladsl.{ Source, Flow }
import org.joda.time._
import slick.dbio._
import scala.util.{ Try, Success, Failure }

@Singleton
class LogService @Inject() (
  dao:                  dal.SharedDataAccessLayer,
  configuration:        play.api.Configuration,
  elasticSearchService: ElasticSearchService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) {

  // deprecate
  def withLog[T](orgId: Int, tags: JsObject)(f: WorkRecord => Future[T]): Future[Option[T]] = {
    val record = WorkRecord(
      Hashing.uuid(),
      Nil,
      Json.toJson(tags),
      orgId,
      WorkStatus.InProgress,
      Some(new DateTime().getMillis()),
      None)
    for {
      _ <- dao.WorkRecordTable.insertOrUpdate(record)
      res <- withRecord(record)(f)
      _ <- finishRecord(record)
    } yield {
      res
    }
  }

  def withWorkId[T](workId: String, onlyPending: Boolean)(f: WorkRecord => Future[T]): Future[Option[T]] = {
    for {
      maybeRecord <- dao.WorkRecordTable.byId.lookup(workId)
      results <- withDefined(maybeRecord) { record =>
        withFlag(!onlyPending || record.status =?= WorkStatus.Pending) {
          withRecord(record)(f)
        }
      }
    } yield {
      results
    }
  }

  def getRecords(workIds: List[String]): Future[List[WorkRecord]] = {
    dao.WorkRecordTable.byId.lookupBatch(workIds).map(_.flatMap(_._2).toList)
  }
  def getRecord(workId: String): Future[Option[WorkRecord]] = {
    dao.WorkRecordTable.byId.lookup(workId)
  }

  def withRecord[T](record: WorkRecord)(f: WorkRecord => Future[T]): Future[Option[T]] = {
    val work = Try(f(record)) match {
      case Success(f) => f.map(Option.apply)
      case Failure(e) => errorRecord(record, e) map (_ => None)
    }
    work.onComplete {
      case Success(_)                => ()
      case Failure(SkipWork(reason)) => skipRecord(record, reason)
      case Failure(e)                => errorRecord(record, e)
    }
    work.recover {
      case e => None
    }
  }

  def createChildren[T](parent: WorkRecord, items: Seq[T])(tags: T => JsObject): Future[List[(T, WorkRecord)]] = {
    val objs = items.map { item =>
      item -> parent.child(tags(item))
    }

    dao.WorkRecordTable.insertBulk(objs.map(_._2)) map { _ =>
      objs.toList
    }
  }

  def createParent(orgId: Int, tags: JsObject): Future[WorkRecord] = {
    val obj = WorkRecord(
      Hashing.uuid(),
      Nil,
      Json.toJson(tags),
      orgId,
      WorkStatus.Pending,
      Some(new DateTime().getMillis()),
      None)
    dao.WorkRecordTable.insertRaw(obj) map (_ => obj)
  }

  def upsertParent(orgId: Int, id: String, tags: JsObject): Future[WorkRecord] = {
    val obj = WorkRecord(
      id,
      Nil,
      Json.toJson(tags),
      orgId,
      WorkStatus.InProgress,
      Some(new DateTime().getMillis()),
      None)
    dao.WorkRecordTable.insertOrUpdate(obj) map (_ => obj)
  }

  def createChild(parent: WorkRecord, tags: JsObject): Future[WorkRecord] = {
    val obj = parent.child(tags)
    dao.WorkRecordTable.insertRaw(obj) map (_ => obj)
  }

  /**
   * State transitions
   */
  def startRecord(record: WorkRecord) = startRecords(List(record))
  def startRecords(records: List[WorkRecord]) = startRecordIds(records.map(_.id))
  def startRecordIds(recordIds: List[String]): Future[Unit] = {
    dao.WorkRecordTable.updateStatusStartedById.updateBatch(
      recordIds, WorkStatus.InProgress, new DateTime().getMillis()) map (_ => ())
  }

  def finishRecord(record: WorkRecord) = finishRecords(List(record))
  def finishRecords(records: List[WorkRecord]) = {
    dao.WorkRecordTable.updateStatusFinishedById.updateBatch(
      records.map(_.id),
      WorkStatus.Complete,
      new DateTime().getMillis())
  }

  import dao.api._
  private def checkFinished(orgId: Int, parentIds: List[List[String]])(callback: WorkRecord => Future[Unit]): Future[Unit] = {
    for {
      initialPass <- Source(parentIds).mapAsync(1) {
        case Nil => Future.successful(None)
        case parentId :: remainder => {
          dao.withTransaction {
            for {
              unfinishedCount <- dao.WorkRecordTable.byParent.lookupQuery(orgId, parentId).filter { i =>
                i.finished.isEmpty
              }.length.result
              complete = unfinishedCount =?= 0
              _ <- withFlag(complete) {
                for {
                  _ <- dao.WorkRecordTable.updateStatusFinishedById.updateQuery(
                    List(parentId),
                    WorkStatus.Complete,
                    new DateTime().getMillis())
                  // run callback
                  maybeParent <- dao.WorkRecordTable.byId.lookupQuery(parentId)
                  _ <- withDefined(maybeParent.headOption) { parent =>
                    DBIOAction.from(callback(parent))
                  }
                } yield {
                  ()
                }
              }
            } yield {
              withFlag(complete) {
                Option(remainder)
              }
            }
          }
        }
      }.runWith(Sinks.ListAccum) map (_.flatten)
      _ <- ifNonEmpty(initialPass) {
        checkFinished(orgId, initialPass)(callback)
      }
    } yield {
      ()
    }
  }

  def sweepRecord(record: WorkRecord) = {
    checkFinished(record.orgId, List(record.parents.reverse))(_ => Future.successful(()))
  }

  def sweepRecords(orgId: Int, records: List[WorkRecord])(callback: WorkRecord => Future[Unit]) = {
    checkFinished(orgId, records.map(_.parents.reverse).distinct)(callback)
  }

  def sweeper(orgId: Int) = {
    Flow[WorkRecord].groupedWithin(100, 5.seconds).mapAsync(1) { records =>
      val parents = records.map(_.parents.reverse).distinct
      checkFinished(orgId, parents.toList)(_ => Future.successful(()))
    }
  }

  private def skipRecord(record: WorkRecord, reason: String) = {
    for {
      _ <- event(s"SKIPPED: ${reason}")(record)
      _ <- dao.WorkRecordTable.updateStatusFinishedById.update(
        record.id,
        WorkStatus.Skipped,
        new DateTime().getMillis())
      // _ <- sweepRecord(record)
    } yield {
      ()
    }
  }

  private def errorRecord(record: WorkRecord, e: Throwable) = {
    val traceLines = e.getStackTrace().map { i =>
      s"- ${i.getFileName()}:${i.getLineNumber()}"
    }.mkString("\n")
    for {
      _ <- event(s"ERROR: ${e}", isError = true)(record)
      _ <- event(traceLines, isError = true)(record)
      _ <- dao.WorkRecordTable.updateStatusFinishedById.update(
        record.id,
        WorkStatus.Error,
        new DateTime().getMillis())
      // _ <- sweepRecord(record)
    } yield {
      ()
    }
  }

  /**
   * Events
   */
  def event(message: String, isError: Boolean = false)(implicit record: WorkRecord): Future[Unit] = {
    println(message)
    elasticSearchService.index(
      WorkLogDocument.globalIndex,
      Json.toJson(
        WorkLogDocument(
          record.id,
          record.parents,
          message,
          isError,
          new DateTime().getMillis())).as[JsObject]) map (_ => ())
  }

  def eventBatch(messages: Seq[String], isError: Boolean = false)(implicit record: WorkRecord): Future[Unit] = {
    val timestamp = new DateTime().getMillis()
    val docs = messages.map { message =>
      // println(message)
      Json.toJson(
        WorkLogDocument(
          record.id,
          record.parents,
          message,
          isError,
          timestamp)).as[JsObject]
    }
    elasticSearchService.indexBulk(
      WorkLogDocument.globalIndex,
      docs) map (_ => ())
  }

  /**
   * Fetch
   */
  def listWorkRecords(orgId: Int): Future[List[WorkTree]] = {
    for {
      parents <- dao.WorkRecordTable.byOrg.lookupTopLevel(orgId).map(_.toList)
      allChildren <- dao.WorkRecordTable.byParent.lookupBatch(orgId, parents.map(_.id))
    } yield {
      parents.map { p =>
        val children = allChildren.filter(_.parents.contains(p.id))

        val skippedMap = statusMapFor(children, WorkStatus.Skipped)
        val erroredMap = statusMapFor(children, WorkStatus.Error)

        p.hydrate(Nil, children, skippedMap, erroredMap)
      }
    }
  }

  private def statusMapFor(children: List[WorkRecord], status: WorkStatus): Map[String, List[String]] = {
    children.filter(_.status =?= status).flatMap { c =>
      c.parents.map(_ -> c.id)
    }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
  }

  def getWorkTree(orgId: Int, workId: String): Future[Option[WorkTree]] = {
    for {
      work <- dao.WorkRecordTable.byId.lookup(workId)
      parentIds = work.toList.flatMap(_.parents)
      parentsRaw <- ifNonEmpty(parentIds) {
        dao.WorkRecordTable.byId.lookupBatch(parentIds)
      }
      parents = parentIds.flatMap { pid =>
        parentsRaw.getOrElse(pid, None)
      }
      allChildren <- withDefined(work) { _ =>
        dao.WorkRecordTable.byParent.lookup(orgId, workId)
      }
    } yield {
      val filtered = allChildren.filter(_.parents.lastOption =?= Some(workId))
      val skippedMap = statusMapFor(allChildren, WorkStatus.Skipped)
      val erroredMap = statusMapFor(allChildren, WorkStatus.Error)

      work.map(_.hydrate(parents, filtered, skippedMap, erroredMap))
    }
  }

  def getLogs(orgId: Int, workId: String, parentOnly: Boolean, errorOnly: Boolean): Future[List[WorkLogDocumentDTO]] = {
    search(workId, parentOnly, errorOnly)
  }

  def getLogItem(logId: String): Future[Option[WorkLogDocumentDTO]] = {
    for {
      raw <- elasticSearchService.get(WorkLogDocument.globalIndex, logId)
    } yield {
      raw.map { i =>
        (i \ "_source").as[WorkLogDocument].dto(logId)
      }
    }
  }

  /**
   * Admin
   */
  def deleteWork(orgId: Int, workId: String): Future[Unit] = {
    dao.withTransaction {
      for {
        _ <- dao.WorkRecordTable.byParent.deleteQuery(orgId, workId)
        _ <- dao.WorkRecordTable.byId.deleteQuery(workId)
      } yield {
        ()
      }
    }
  }

  def cycleLogIndex() = {
    for {
      _ <- elasticSearchService.dropIndex(WorkLogDocument.globalIndex)
      resp <- elasticSearchService.createIndex(WorkLogDocument.globalIndex, WorkLogDocument.mappings)
    } yield {
      resp
    }
  }

  def getLogIndex() = {
    elasticSearchService.getIndex(WorkLogDocument.globalIndex)
  }

  /**
   * BTL
   */
  private def search(workId: String, parentOnly: Boolean, errorOnly: Boolean): Future[List[WorkLogDocumentDTO]] = {
    val errorFlag = withFlag(errorOnly) {
      ESQuery.termSearch("is_error", "true") :: Nil
    }

    val shoulds = ESQuery.bool(must = ESQuery.termSearch("work_id", workId) :: errorFlag) :: withFlag(!parentOnly) {
      ESQuery.bool(must = ESQuery.termSearch("parents", workId) :: errorFlag) :: Nil
    }

    for {
      raw <- elasticSearchService.search(
        WorkLogDocument.globalIndex,
        ESQuery.bool(should = shoulds),
        additional = Json.obj(
          "sort" -> JsArray(List(Json.obj(
            "time" -> Json.obj(
              "order" -> "desc")))),
          "size" -> 500))
    } yield {
      (raw \ "hits" \ "hits").as[List[JsValue]].map { i =>
        val logId = (i \ "_id").as[String]
        (i \ "_source").as[WorkLogDocument].dto(logId)
      }.reverse
    }
  }
}
