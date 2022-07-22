import javax.inject._
import silvousplay.imports._
import com.google.inject.AbstractModule
import scala.concurrent.ExecutionContext
import services._

@Singleton
class ApplicationStart @Inject() (
  repoSyncService: LocalRepoSyncService,
  scanService:     LocalScanService,
  configuration:   play.api.Configuration)(implicit ec: ExecutionContext) {

  for {
    // initial scan
    _ <- withFlag(repoSyncService.UseWatcher) {
      scanService.initialScan(orgId = -1)
    }
    _ <- withFlag(repoSyncService.UseWatcher) {
      repoSyncService.syncAllWatches()
    }
    // do consume
  } yield {
    println("QUEUE INITIALIZATION COMPLETE")
  }
}

class StartModule extends AbstractModule {
  override def configure() = {
    bind(classOf[AuthService]).to(classOf[LocalAuthService])
    bind(classOf[RepoDataService]).to(classOf[LocalRepoDataService])
    bind(classOf[GitService]).to(classOf[LocalGitService])
    bind(classOf[FileService]).to(classOf[LocalFileService])
    bind(classOf[RepoSyncService]).to(classOf[LocalRepoSyncService])
    bind(classOf[ScanService]).to(classOf[LocalScanService])
    bind(classOf[ConsumerService]).to(classOf[WebhookConsumerService])

    bind(classOf[ApplicationStart])
      .asEagerSingleton()
  }
}
