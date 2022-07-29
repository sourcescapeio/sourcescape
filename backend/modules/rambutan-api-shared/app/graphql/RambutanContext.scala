package graphql

import javax.inject._
import services._

@Singleton
class RambutanContext @Inject() (
  configuration:            play.api.Configuration,
  val localRepoDataService: LocalRepoDataService,
  val repoIndexDataService: RepoIndexDataService,
  val localScanService:     LocalScanService) {

}
