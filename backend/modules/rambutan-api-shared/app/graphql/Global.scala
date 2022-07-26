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
    "Query", fields[RambutanContext, Unit](
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
      "Mutation", fields[RambutanContext, Unit](
        Field("createScan", Scan,
          arguments = PathArg :: Nil,
          resolve = ctx => ctx.ctx.localScanService.createScan(ctx.arg(PathArg)))))
  }

  val RambutanSchema = sangria.schema.Schema(Query, Some(Mutation))

  // also define fetchers here
}

@Singleton
class RambutanContext @Inject() (
  configuration:        play.api.Configuration,
  val localScanService: LocalScanService) {

}