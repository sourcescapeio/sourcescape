package services

import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import models._
import play.api.libs.json._
import org.joda.time._
import akka.stream.scaladsl.Source
import models.graph._
import silvousplay.api.SpanContext

trait ConsumerService {

  implicit val ec: ExecutionContext

  val repoIndexDataService: RepoIndexDataService
  val indexerService: IndexerService
  val logService: LogService
  val clonerQueueService: ClonerQueueService

  protected def getRootId(orgId: Int, repoId: Int, sha: String, includeSelf: Boolean)(implicit context: SpanContext): Future[Option[Int]] = {
    for {
      shaObj <- repoIndexDataService.getSHA(repoId, sha).map {
        _.getOrElse(throw new Exception("sha does not exist"))
      }
      belowChain <- repoIndexDataService.getBelowChain(orgId, repoId, sha).map {
        case items if includeSelf => shaObj :: items
        case items                => items
      }
      aboveChain <- repoIndexDataService.getAboveChain(orgId, repoId, sha)
      // TODO: can add back above chain
      chain = belowChain ++ aboveChain
      chainIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, chain.map(_.sha)).map {
        _.groupBy(_.sha)
      }
      belowRoot = belowChain.flatMap { chainSHA =>
        chainIndexes.getOrElse(chainSHA.sha, Nil).find(_.isRoot)
      }.headOption
      rootIndex = belowRoot.orElse {
        aboveChain.flatMap { chainSHA =>
          chainIndexes.getOrElse(chainSHA.sha, Nil).find(_.isRoot)
        }.headOption
      }
    } yield {
      // println("ROOT", rootIndex)
      // println("BELOW CHAIN")
      // belowChain.foreach(println)
      // println("ABOVE CHAIN")
      // aboveChain.foreach(println)
      rootIndex.map(_.id)
    }
  }

  // Either[Index, WorkRecord]
  def runCleanIndexForSHA(orgId: Int, repoName: String, repoId: Int, sha: String, forceRoot: Boolean)(implicit context: SpanContext): Future[Either[RepoSHAIndex, (RepoSHAIndex, WorkRecord)]] = {
    for {
      currentIndexes <- repoIndexDataService.getIndexesForRepoSHAs(repoId, List(sha))
      clean = currentIndexes.find(a => a.isRoot)
      hasClean = clean.isDefined
      diff = currentIndexes.find(a => a.isDiff && !a.dirty)
      maybeRootId <- withFlag(!hasClean && !forceRoot) {
        getRootId(orgId, repoId, sha, includeSelf = false)
      }
      // do queue
      res <- (clean, diff) match {
        case (Some(index), _) => {
          println(s"SKIPPED ${sha} AS WE ALREADY HAVE A ROOT")
          Future.successful {
            Left(index)
          }
        }
        case (_, Some(index)) if maybeRootId.isDefined => {
          println(s"SKIPPED ${sha} AS WE ALREADY HAVE A DIFF")
          Future.successful {
            Left(index)
          }
        }
        case _ => {
          for {
            parent <- logService.createParent(orgId, Json.obj(
              "repoId" -> repoId,
              "sha" -> sha))
            obj = RepoSHAIndex(0, orgId, repoName, repoId, sha, maybeRootId, dirtySignature = None, parent.id, deleted = false, new DateTime().getMillis())
            index <- repoIndexDataService.writeIndex(obj)(parent)
            item = ClonerQueueItem(orgId, repoId, index.id, None, parent.id)
            // _ = println("Cloning", item)
            _ <- clonerQueueService.enqueue(item)
          } yield {
            Right((index, parent))
          }
        }
      }
    } yield {
      res
    }
  }
}
