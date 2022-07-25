package services

import javax.inject._
import models._
import play.api.mvc._
import scala.concurrent.{ ExecutionContext, Future }

// NOOP for local
class LocalAuthService @Inject() (
  repoDataService: LocalRepoDataService)(implicit ec: ExecutionContext) extends AuthService {

  def authenticated[T](f: => Future[T])(implicit request: RequestHeader): Future[T] = {
    f
  }

  def authenticatedSuperUser[T](f: => Future[T])(implicit request: RequestHeader): Future[T] = {
    f
  }

  def authenticatedForOrg[T](orgId: Int, role: OrgRole)(f: => Future[T])(implicit request: RequestHeader): Future[T] = {
    f
  }

  def orgsAuthenticatedFor[T](f: List[Int] => Future[T])(implicit request: RequestHeader): Future[T] = {
    f(List(-1))
  }

  def authenticatedReposForOrg[T](orgId: Int, role: RepoRole)(f: List[GenericRepo] => Future[T])(implicit request: RequestHeader): Future[T] = {
    for {
      repos <- repoDataService.getReposForOrg(orgId)
      r <- f(repos)
    } yield {
      r
    }
  }

  def authenticatedForRepo[T](orgId: Int, repoId: Int, role: RepoRole)(f: => Future[T])(implicit request: RequestHeader): Future[T] = {
    f
  }
}
