package graphql

import models.LocalScanDirectory

import sangria.execution._
import sangria.parser.{ QueryParser, SyntaxError }
import sangria.marshalling.playJson._
import sangria.execution.deferred.DeferredResolver
import sangria.renderer.SchemaRenderer
import sangria.slowlog.SlowLog

// models
import sangria.execution.deferred.{ Fetcher, HasId }
import sangria.schema._
import sangria.macros.derive._
import sangria.streaming.akkaStreams._
import services.EventMessage
import services.SocketEventType
import models.LocalRepoConfig
import models.RepoSHAIndex

import silvousplay.imports._
import models.RepoCollectionIntent
import models.RepoSettings

// Question: how to define GraphQL context
object SchemaDefinition {
  // Fetchers
  val repoByScanFetcher = {
    Fetcher(
      (ctx: RambutanContext, scanIds: Seq[Int]) => {
        println("FETCHING REPOS", scanIds)
        import ctx.ec
        ctx.localRepoDataService.getReposByScanBatch(scanIds).map(_.toList)
      })(HasId[(Int, List[LocalRepoConfig]), Int](_._1))
  }

  val indexByRepoFetcher = {
    Fetcher(
      (ctx: RambutanContext, repoIds: Seq[Int]) => {
        println("FETCHING INDEXES", repoIds)
        import ctx.ec
        ctx.repoIndexDataService.getIndexesForRepo(repoIds.toList).map(_.toList)
      })(HasId[(Int, List[RepoSHAIndex]), Int](_._1))
  }

  val settingsByRepoFetcher = {
    Fetcher(
      (ctx: RambutanContext, repoIds: Seq[Int]) => {
        println("FETCHING SETTINGS", repoIds)
        import ctx.ec
        ctx.localRepoDataService.getRepoSettings(repoIds.toList).map(_.toList)
      })(HasId[(Int, Option[RepoSettings]), Int](_._1))
  }

  val messageByIdFetcher = {
    Fetcher(
      (ctx: RambutanContext, ids: Seq[(SocketEventType, String)]) => {
        import ctx.ec
        ctx.socketService.getMessageBatch(ids).map(_.toList)
      })(HasId[((SocketEventType, String), Option[EventMessage]), (SocketEventType, String)](_._1))
  }

  // Args
  val ScanID = Argument("id", IntType, description = "id of the scan")
  val RepoID = Argument("id", IntType, description = "id of the repo")

  /**
   * Objects
   */
  val RepoIndexType = ObjectType(
    "RepoIndex",
    "Index for a repo",
    fields[RambutanContext, RepoSHAIndex](
      Field("id", IntType,
        Some("the id of the index"),
        resolve = _.value.id),
      Field("sha", StringType,
        Some("the sha of the index"),
        resolve = _.value.sha),
      Field("dirty", BooleanType,
        Some("whether this is a dirty index"),
        resolve = _.value.dirtySignature.isDefined),
      Field("cloneProgress", IntType,
        Some("clone progress"),
        resolve = { ctx =>
          import ctx.ctx.ec
          DeferredValue(messageByIdFetcher.deferOpt((SocketEventType.CloningStarted, ctx.value.id.toString()))).map {
            case Some((i, Some(v))) => {
              (v.data \ "progress").asOpt[Int].getOrElse(0)
            }
            case _ => 0
          }
        }),
      Field("indexProgress", IntType,
        Some("index progress"),
        resolve = { ctx =>
          import ctx.ctx.ec
          DeferredValue(messageByIdFetcher.deferOpt((SocketEventType.IndexingStarted, ctx.value.id.toString()))).map {
            case Some((i, Some(v))) => {
              println(v.data)
              (v.data \ "progress").asOpt[Int].getOrElse(0)
            }
            case _ => 0
          }
        })))

  val RepoCollectionIntentEnum = deriveEnumType[RepoCollectionIntent]()

  val Repo = ObjectType(
    "Repo",
    "A scanned repo",
    fields[RambutanContext, LocalRepoConfig](
      Field("id", IntType,
        Some("the id of the repo"),
        resolve = _.value.repoId),
      Field("name", StringType,
        Some("the name of the repo"),
        resolve = _.value.repoName),
      Field("path", StringType,
        Some("the path"),
        resolve = _.value.localPath),
      Field("intent", RepoCollectionIntentEnum,
        resolve = ctx => {
          import ctx.ctx.ec
          DeferredValue(settingsByRepoFetcher.deferOpt(ctx.value.repoId)).map {
            case Some((_, Some(v))) => v.intent
            case _                  => RepoCollectionIntent.Skip
          }
        }),
      // Field("progress", IntType,
      //   Some("progress for this repo"),
      //   resolve = ctx => {
      //     import ctx.ctx.ec
      //     DeferredValue(indexByRepoFetcher.deferOpt(ctx.value.repoId)).map {
      //       // how do we know which branch?
      //     }
      //   }),
      Field("indexes", ListType(RepoIndexType),
        Some("indexes for the repo"),
        resolve = ctx => {
          import ctx.ctx.ec
          DeferredValue(indexByRepoFetcher.deferOpt(ctx.value.repoId)).map {
            case None    => Nil
            case Some(i) => i._2
          }
        })))

  val Scan = ObjectType(
    "Scan",
    "A directory to scan",
    // interfaces[RambutanContext, LocalScanDirectory](Character), << I don't think we extend anything
    fields[RambutanContext, LocalScanDirectory](
      Field("id", IntType,
        Some("the id of the scan"),
        resolve = _.value.id),
      Field("path", StringType,
        Some("the path we're scanning"),
        resolve = _.value.path),
      Field("progress", OptionType(IntType),
        Some("the path"),
        resolve = ctx => {
          import ctx.ctx.ec
          DeferredValue(messageByIdFetcher.deferOpt((SocketEventType.ScanStartup, ctx.value.id.toString()))).map {
            case Some((i, Some(v))) => {
              (v.data \ "progress").asOpt[Int].getOrElse(0)
            }
            case _ => 0
          }
        }),
      Field("repos", ListType(Repo),
        Some("repos for this scan"),
        resolve = ctx => {
          import ctx.ctx.ec
          DeferredValue(repoByScanFetcher.deferOpt(ctx.value.id)).map {
            case None    => Nil
            case Some(i) => i._2
          }
        })))

  /**
   * Top level objects
   */
  val Query = ObjectType(
    "Query", fields[RambutanContext, Any](
      // need to list all objects
      Field("scan", OptionType(Scan),
        arguments = ScanID :: Nil,
        resolve = ctx => ctx.ctx.localScanService.getScanById(ctx.arg(ScanID))),
      Field("scans", ListType(Scan),
        arguments = Nil,
        resolve = ctx => ctx.ctx.localScanService.listScans()),
      Field("repos", ListType(Repo),
        arguments = Nil,
        resolve = ctx => {
          import ctx.ctx.ec
          ctx.ctx.localRepoDataService.getAllLocalRepos().map(_.sortBy(_.repoName)) // TODO: by org id
        })))

  val Mutation = {
    val PathArg = Argument("path", StringType, description = "path of the scan")

    ObjectType(
      "Mutation", fields[RambutanContext, Any](
        Field("createScan", Scan,
          arguments = PathArg :: Nil,
          resolve = ctx => ctx.ctx.localScanService.createScan(-1, ctx.arg(PathArg), shouldScan = true)),
        Field("deleteScan", OptionType(Scan),
          arguments = ScanID :: Nil,
          resolve = ctx => ctx.ctx.localScanService.deleteScan(-1, ctx.arg(ScanID))),
        Field("selectRepo", IntType,
          arguments = RepoID :: Nil,
          resolve = ctx => ctx.ctx.localRepoSyncService.setRepoIntent(-1, ctx.arg(RepoID), RepoCollectionIntent.Collect, queue = true))))
  }

  /**
   * Subscriptions
   */
  trait Event {
    def id: String
    def version: Long
  }

  val EventType = InterfaceType("Event", fields[RambutanContext, Event](
    Field("id", StringType, resolve = _.value.id)))

  case class ScanProgress(
    id:       String, // scanId
    version:  Long,
    progress: Int) extends Event

  case class CloneProgress(
    id:       String, // indexId
    version:  Long,
    indexId:  Int,
    repoId:   Int,
    progress: Int) extends Event

  case class IndexProgress(
    id:       String, // indexId
    version:  Long,
    indexId:  Int,
    repoId:   Int,
    progress: Int) extends Event

  val ScanProgressType = deriveObjectType[RambutanContext, ScanProgress](Interfaces(EventType))
  val CloneProgressType = deriveObjectType[RambutanContext, CloneProgress](Interfaces(EventType))
  val IndexProgressType = deriveObjectType[RambutanContext, IndexProgress](Interfaces(EventType))

  val SubscriptionType = ObjectType(
    "Subscription",
    fields[RambutanContext, Any](
      Field("indexProgress", OptionType(IndexProgressType), resolve = (c: Context[RambutanContext, Any]) => {
        val msg = c.value.asInstanceOf[EventMessage]
        msg.eventType match {
          case SocketEventType.IndexingStarted => Option(
            IndexProgress(
              msg.id,
              0L,
              (msg.data \ "indexId").as[Int],
              (msg.data \ "repoId").as[Int],
              (msg.data \ "progress").as[Int]))
          case SocketEventType.IndexingFinished => Option(
            IndexProgress(
              msg.id,
              0L,
              (msg.data \ "indexId").as[Int],
              (msg.data \ "repoId").as[Int],
              100))
          case _ => None
        }
      }),
      Field("cloneProgress", OptionType(CloneProgressType), resolve = (c: Context[RambutanContext, Any]) => {
        val msg = c.value.asInstanceOf[EventMessage]
        msg.eventType match {
          case SocketEventType.CloningStarted => Option(
            CloneProgress(
              msg.id,
              0L,
              (msg.data \ "indexId").as[Int],
              (msg.data \ "repoId").as[Int],
              (msg.data \ "progress").as[Int]))
          case SocketEventType.CloningFinished => Option(
            CloneProgress(
              msg.id,
              0L,
              (msg.data \ "indexId").as[Int],
              (msg.data \ "repoId").as[Int],
              100))
          case _ => None
        }
      }),
      Field("scanProgress", OptionType(ScanProgressType), resolve = (c: Context[RambutanContext, Any]) => {
        val msg = c.value.asInstanceOf[EventMessage]
        msg.eventType match {
          case SocketEventType.ScanStartup => Option(
            ScanProgress(
              msg.id,
              0L,
              (msg.data \ "progress").as[Int]))
          case SocketEventType.ScanFinished => Option(
            ScanProgress(
              msg.id,
              0L,
              100))
          case _ => None
        }
      })))

  val RambutanSchema = sangria.schema.Schema(Query, Some(Mutation), Some(SubscriptionType))

  val Resolvers = DeferredResolver.fetchers(
    repoByScanFetcher,
    indexByRepoFetcher,
    settingsByRepoFetcher,
    messageByIdFetcher)

  // also define fetchers here
}
