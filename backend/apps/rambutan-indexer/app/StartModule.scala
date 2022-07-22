import javax.inject._
import silvousplay.imports._
import com.google.inject.AbstractModule
import scala.concurrent.ExecutionContext
import services._
import workers._

@Singleton
class ApplicationStart @Inject() (
  configuration:          play.api.Configuration,
  queueManagementService: QueueManagementService,
  // idempotent queues
  clonerService:          ClonerService,
  compilerService:        CompilerService,
  cachingService:         CachingService,
  webhookConsumerService: WebhookConsumerService,
  //
  indexerWorker:     IndexerWorker,
  snapshotterWorker: SnapshotterWorker,
  // sweepers
  indexSweeperService: IndexSweeperService,
  cacheSweeperService: CacheSweeperService)(implicit ec: ExecutionContext) {

  for {
    _ <- queueManagementService.wipeQueues()
    _ <- webhookConsumerService.startWebhookConsumer()
    _ <- clonerService.startCloner()
    _ <- indexerWorker.startRepoIndexing()
    _ <- compilerService.startRepoCompilation()
    _ <- cachingService.startCaching()
    _ <- snapshotterWorker.startSnapshotter()
    // cron-based sweepers
    _ <- indexSweeperService.startSweeper()
    _ <- indexSweeperService.startDeletion()
    _ <- cacheSweeperService.startSweeper()
    _ <- cacheSweeperService.startDeletion()
  } yield {
    println("QUEUE INITIALIZATION COMPLETE")
  }
}

class StartModule extends AbstractModule {
  override def configure() = {
    bind(classOf[RepoDataService]).to(classOf[LocalRepoDataService])
    bind(classOf[GitService]).to(classOf[LocalGitService])
    bind(classOf[FileService]).to(classOf[LocalFileService])

    bind(classOf[ApplicationStart])
      .asEagerSingleton()
  }
}
