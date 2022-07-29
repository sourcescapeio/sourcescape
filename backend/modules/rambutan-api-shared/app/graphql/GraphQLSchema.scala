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

// Question: how to define GraphQL context
object SchemaDefinition {
  val ScanID = Argument("id", IntType, description = "id of the scan")

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
    )
  )

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
      Field("indexes", ListType(RepoIndexType),
        Some("indexes for the repo"),
        resolve = ctx => ctx.ctx.repoIndexDataService.getIndexesForRepo(ctx.value.repoId)
      )
    ))

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
      Field("repos", ListType(Repo),
        Some("repos for this scan"),
        resolve = ctx => ctx.ctx.localRepoDataService.getReposByScan(ctx.value.id))))

  val Query = ObjectType(
    "Query", fields[RambutanContext, Any](
      // need to list all objects
      Field("scan", OptionType(Scan),
        arguments = ScanID :: Nil,
        resolve = ctx => ctx.ctx.localScanService.getScanById(ctx.arg(ScanID))),
      Field("scans", ListType(Scan),
        arguments = Nil,
        resolve = ctx => ctx.ctx.localScanService.listScans())))

  val Mutation = {
    val PathArg = Argument("path", StringType, description = "path of the scan")

    ObjectType(
      "Mutation", fields[RambutanContext, Any](
        Field("createScan", Scan,
          arguments = PathArg :: Nil,
          resolve = ctx => ctx.ctx.localScanService.createScan(-1, ctx.arg(PathArg), true)),
        Field("selectRepos", Scan,
          arguments = PathArg :: Nil,
          resolve = ctx => ctx.ctx.localScanService.createScan(-1, ctx.arg(PathArg), true))          
      ))
  }

  trait Event {
    def id: String
    def version: Long
  }

  val EventType = InterfaceType("Event", fields[RambutanContext, Event](
    Field("id", StringType, resolve = _.value.id)))

  case class ScanProgress(
    id:       String,
    version:  Long,
    progress: Int) extends Event

  // case class ScanProgress(id: String, version: Long, ....)

  val ScanProgressType = deriveObjectType[RambutanContext, ScanProgress](Interfaces(EventType))

  // val AuthorNameChangedType = deriveObjectType[Unit, AuthorNameChanged](Interfaces(EventType))
  // val AuthorDeletedType = deriveObjectType[Unit, AuthorDeleted](Interfaces(EventType))

  // val ArticleCreatedType = deriveObjectType[Unit, ArticleCreated](Interfaces(EventType))
  // val ArticleTextChangedType = deriveObjectType[Unit, ArticleTextChanged](Interfaces(EventType))
  // val ArticleDeletedType = deriveObjectType[Unit, ArticleDeleted](Interfaces(EventType))

  // val SubscriptionFields = Map[String, SubscriptionField[Event]](
  //   "scanProgress" -> Field.subs()

  //     SubscriptionField (ScanProgressType)
  // "authorNameChanged" → SubscriptionField(AuthorNameChangedType),
  // "authorDeleted" → SubscriptionField(AuthorDeletedType),
  // "articleCreated" → SubscriptionField(ArticleCreatedType),
  // "articleTextChanged" → SubscriptionField(ArticleTextChangedType),
  // "articleDeleted" → SubscriptionField(ArticleDeletedType))
  // )

  val SubscriptionType = ObjectType(
    "Subscription",
    fields[RambutanContext, Any](
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

  // also define fetchers here
}
