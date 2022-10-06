package graphql

import javax.inject._
import services._
import scala.concurrent.ExecutionContext
import akka.stream.Materializer

@Singleton
class RambutanContext @Inject() (
  configuration:            play.api.Configuration,
  val localRepoDataService: LocalRepoDataService,
  val repoIndexDataService: RepoIndexDataService,
  val socketService:        SocketService,
  val repoIndexingService:  RepoIndexingService,
  val localScanService:     LocalScanService,
  val repoService:          RepoService)(implicit val ec: ExecutionContext, val mat: Materializer) {

}
