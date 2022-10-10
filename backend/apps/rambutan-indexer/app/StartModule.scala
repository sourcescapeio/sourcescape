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
  // sweepers
  indexSweeperService: IndexSweeperService)(implicit ec: ExecutionContext) {

  for {
    _ <- queueManagementService.wipeQueues()
    // cron-based sweepers
    _ <- indexSweeperService.startSweeper()
    _ <- indexSweeperService.startDeletion()
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
