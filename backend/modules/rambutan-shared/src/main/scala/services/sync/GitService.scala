package services

import models._
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.Source
import akka.util.ByteString

protected case class GitDiff(added: Set[String], deleted: Set[String], modified: Set[String]) { // + renamed
  def addPaths = added ++ modified

  def size = (added ++ deleted ++ modified).size

  def isEmpty = added.isEmpty && deleted.isEmpty && modified.isEmpty

  def reverse = this.copy(added = deleted, deleted = added)
}

object GitDiff {
  def empty = GitDiff(Set.empty[String], Set.empty[String], Set.empty[String])
}

case class RepoInfo(sha: String, shaMessage: String, currentBranch: Option[String], statusDiff: GitDiff) {
  def isClean = statusDiff.isEmpty
}

case class RepoCommit(sha: String, parents: List[String], message: String, branch: String) {
  def repoSHA(repoId: Int, branches: List[String], refs: List[String]) = {
    RepoSHA(repoId, sha, parents, branches, refs, message)
  }
}

protected case class RepoScan(branches: List[String], shas: List[RepoSHA])

case class GitScanResult(localDir: String, valid: Boolean, remotes: Set[String])

trait GitServiceRepo {
  def getRepoInfo: Future[RepoInfo]

  def getCommitChain(branch: String): Source[RepoCommit, Any]

  def getTreeAt(sha: String): Future[Set[String]]

  def resolveDiff(oldSHA: String, newSHA: String): Future[GitDiff]

  def getFilesAt(files: Set[String], sha: String): Future[Map[String, ByteString]]

  def getRepoBranches: Future[Map[String, String]]

  def close(): Unit
}

trait GitService {

  def getGitRepo(repo: GenericRepo): Future[GitServiceRepo]

  def withRepo[T](repo: GenericRepo)(f: GitServiceRepo => Future[T])(implicit ec: ExecutionContext) = {
    for {
      repo <- getGitRepo(repo)
      res <- f(repo)
    } yield {
      repo.close()
      res
    }
  }
}
