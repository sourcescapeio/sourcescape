package graphql

import javax.inject._
import services._
import scala.concurrent.ExecutionContext

@Singleton
class RambutanContext @Inject() (
  configuration:            play.api.Configuration,
  val localRepoDataService: LocalRepoDataService,
  val repoIndexDataService: RepoIndexDataService,
  val socketService:        SocketService,
  val localRepoSyncService: LocalRepoSyncService,
  val localScanService:     LocalScanService)(implicit val ec: ExecutionContext) {

}
