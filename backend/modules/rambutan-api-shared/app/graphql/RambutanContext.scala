package graphql

import javax.inject._
import services._
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import silvousplay.api.TelemetryService

@Singleton
class RambutanContext @Inject() (
  configuration:            play.api.Configuration,
  val telemetryService:     TelemetryService,
  val localRepoDataService: LocalRepoDataService,
  val repoIndexDataService: RepoIndexDataService,
  val socketService:        SocketService,
  val repoIndexingService:  RepoIndexingService,
  val repoService:          RepoService)(implicit val ec: ExecutionContext, val mat: Materializer) {

}
