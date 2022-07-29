package graphql

import models.LocalScanDirectory
import services.LocalScanService

import javax.inject._

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

// Question: how to define GraphQL context
// let's use a graphql directory with all graphql stuff
// hydrated should basically all go in there
// this should then have a trait that essentially defers to necessary objects in the cake?
object SchemaDefinition {
  val ScanID = Argument("id", IntType, description = "id of the scan")

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
        resolve = _.value.path)))

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
          resolve = ctx => ctx.ctx.localScanService.createScan(ctx.arg(PathArg)))))
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
          case _ => None
        }
      })))

  val RambutanSchema = sangria.schema.Schema(Query, Some(Mutation), Some(SubscriptionType))

  // also define fetchers here
}

@Singleton
class RambutanContext @Inject() (
  configuration:        play.api.Configuration,
  val localScanService: LocalScanService) {

}