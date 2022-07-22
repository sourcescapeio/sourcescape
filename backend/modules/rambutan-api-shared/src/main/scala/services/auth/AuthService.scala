package services

import models._
import play.api.mvc._
import scala.concurrent.Future

trait AuthService {

  // global level
  def authenticated[T](f: => Future[T])(implicit request: RequestHeader): Future[T]

  def authenticatedSuperUser[T](f: => Future[T])(implicit request: RequestHeader): Future[T]

  // org level
  def authenticatedForOrg[T](orgId: Int, role: OrgRole)(f: => Future[T])(implicit request: RequestHeader): Future[T]

  def orgsAuthenticatedFor[T](f: List[Int] => Future[T])(implicit request: RequestHeader): Future[T]

  // repo level
  def authenticatedReposForOrg[T](orgId: Int, role: RepoRole)(f: List[GenericRepo] => Future[T])(implicit request: RequestHeader): Future[T]

  def authenticatedForRepo[T](orgId: Int, repoId: Int, role: RepoRole)(f: => Future[T])(implicit request: RequestHeader): Future[T]
}
