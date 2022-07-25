import javax.inject._
import silvousplay.imports._
import com.google.inject.AbstractModule
import scala.concurrent.ExecutionContext
import services._

class StartModule extends AbstractModule {
  override def configure() = {
    // bind(classOf[RepoDataService]).to(classOf[GithubRepoDataService])
    // bind(classOf[AuthService]).to(classOf[WebAuthService])
    // bind(classOf[GitService]).to(classOf[GithubService])
    // bind(classOf[FileService]).to(classOf[S3FileService])
    // bind(classOf[RepoSyncService]).to(classOf[GithubRepoSyncService])
    // bind(classOf[ConsumerService]).to(classOf[GithubConsumerService])
  }
}
