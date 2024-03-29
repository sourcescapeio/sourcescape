import javax.inject._
import silvousplay.imports._
import com.google.inject.AbstractModule
import scala.concurrent.ExecutionContext
import services._

class StartModule extends AbstractModule {
  override def configure() = {
    bind(classOf[AuthService]).to(classOf[LocalAuthService])
    bind(classOf[RepoDataService]).to(classOf[LocalRepoDataService])
    bind(classOf[FileService]).to(classOf[LocalFileService])
  }
}
