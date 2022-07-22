package services

import models._
import models.index.{ GraphEdge, GraphResult }
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._

import akka.stream.scaladsl.Source

// for jgit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{ Repository, ObjectId }
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.NullOutputStream
import org.eclipse.jgit.diff.{ DiffFormatter, DiffEntry }
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit

//
import scala.jdk.CollectionConverters._
import akka.util.ByteString
import java.io.File

@Singleton
class LocalGitService @Inject() (
  configuration:        play.api.Configuration,
  repoIndexDataService: RepoIndexDataService,
  repoDataService:      LocalRepoDataService)(implicit ec: ExecutionContext, mat: akka.stream.Materializer) extends GitService {

  case class LocalRepoContainer(localDir: String, repo: Repository)(implicit ec: ExecutionContext) extends GitServiceRepo {
    val gitObj = new Git(repo)

    def getRepoInfo: Future[RepoInfo] = {

      for {
        statusDiff <- Future {
          val status = gitObj.status().call()
          GitDiff(
            added = status.getAdded().asScala.toSet, // we require untracked to be manually added
            deleted = (status.getMissing().asScala ++ status.getRemoved().asScala).toSet,
            modified = (status.getChanged().asScala ++ status.getModified().asScala).toSet
          // ignore conflicting
          )
        }
        (sha, shaMessage) = {
          val head = repo.getAllRefs().get("HEAD")
          val headObject = head.getObjectId()
          val message = scala.util.Using(new RevWalk(repo)) { revWalk =>
            revWalk.parseCommit(headObject).getFullMessage()
          }
          (headObject.getName(), message.toOption.getOrElse(""))
        }
        refRegex = "^refs/heads/(.+)".r
        currentBranch = Option(repo.getFullBranch()) match {
          case Some(refRegex(branch)) => Some(branch)
          case _                      => None
        }
      } yield {
        RepoInfo(
          sha,
          shaMessage,
          currentBranch,
          statusDiff)
      }
    }

    val ChainMax = 2000
    def getCommitChain(branch: String): Source[RepoCommit, Any] = {
      val shaObj = repo.resolve(branch)
      Source.fromIterator { () =>
        gitObj.log().add(shaObj).setMaxCount(ChainMax).call().iterator().asScala
      }.map { revCommit =>
        val parents = revCommit.getParents().toList.map(_.getName())
        // val tree = revCommit.getTree().getName()
        val message = revCommit.getShortMessage()
        val sha = revCommit.getName()
        RepoCommit(sha, parents, message, branch)
      }
    }

    def getRepoBranches: Future[Map[String, String]] = Future {
      gitObj.branchList().call().asScala.map { b =>
        val branch = b.getName().replaceFirst("^refs/heads/", "")
        val target = b.getObjectId().getName()
        branch -> target
      }.toMap
    }

    def getTreeAt(sha: String): Future[Set[String]] = Future {
      // val shaObject = Option(repo.getAllRefs().get(s"${sha}^{tree}"))

      scala.util.Using(new RevWalk(repo)) { revWalk =>
        val commitId = ObjectId.fromString(sha);
        val tree = revWalk.parseCommit(commitId).getTree()
        scala.util.Using(new TreeWalk(repo)) { treeWalk =>
          treeWalk.addTree(tree)
          treeWalk.setRecursive(true)
          val iter = new Iterator[String] {
            def hasNext = treeWalk.next()
            def next = treeWalk.getPathString()
          }

          iter.toSet
        }.toOption
      }.toOption.getOrElse(None).getOrElse(Set.empty[String])
    }

    def resolveDiff(oldSHA: String, newSHA: String): Future[GitDiff] = {
      val reader = gitObj.getRepository().newObjectReader()

      val oldTree = repo.resolve(s"${oldSHA}^{tree}")
      val newTree = repo.resolve(s"${newSHA}^{tree}")

      Future {
        val oldTreeIter = new CanonicalTreeParser()
        oldTreeIter.reset(reader, oldTree)
        val newTreeIter = new CanonicalTreeParser()
        newTreeIter.reset(reader, newTree)

        val df = new DiffFormatter(NullOutputStream.INSTANCE)
        df.setRepository(repo)
        val allDiffs = df.scan(oldTreeIter, newTreeIter).asScala.toList

        val added = allDiffs.flatMap {
          case d if d.getChangeType() =?= DiffEntry.ChangeType.ADD => {
            Some(d.getNewPath())
          }
          case _ => None
        }
        val deleted = allDiffs.flatMap {
          case d if d.getChangeType() =?= DiffEntry.ChangeType.DELETE => {
            Some(d.getOldPath())
          }
          case _ => None
        }
        val modified = allDiffs.flatMap {
          case d if d.getChangeType() =?= DiffEntry.ChangeType.MODIFY => {
            Some(d.getNewPath())
          }
          case _ => None
        }
        reader.close()

        GitDiff(added.toSet, deleted.toSet, modified.toSet)
      }
    }

    def getFilesAt(files: Set[String], sha: String): Future[Map[String, ByteString]] = Future {
      scala.util.Using(new RevWalk(repo)) { revWalk =>
        val commitId = ObjectId.fromString(sha);
        val tree = revWalk.parseCommit(commitId).getTree()

        scala.util.Using(new TreeWalk(repo)) { treeWalk =>
          treeWalk.addTree(tree)
          treeWalk.setRecursive(true)

          val iter = new Iterator[Option[(String, ByteString)]] {
            def hasNext = treeWalk.next()
            def next = {
              treeWalk.getPathString() match {
                case fileName if files.contains(fileName) => {
                  val blobId = treeWalk.getObjectId(0)

                  scala.util.Using(repo.newObjectReader()) { objectReader =>
                    val bytes = objectReader.open(blobId).getBytes()
                    (fileName, ByteString(bytes))
                  }.toOption
                }
                case _ => None
              }
            }
          }

          iter.flatten.toMap
        }.toOption
      }.toOption.getOrElse(None).getOrElse(Map.empty[String, ByteString])
    }

    def close() = {
      gitObj.close()
      repo.close()
    }
  }

  def getGitRepo(repo: GenericRepo): Future[GitServiceRepo] = {
    getGitRepoInner(repo.asInstanceOf[LocalRepoConfig].localPath)
  }

  private def getGitRepoInner(repoDir: String): Future[LocalRepoContainer] = {
    val dir = repoDir + "/.git"

    Future {
      val builder = new FileRepositoryBuilder()
      val repo = builder.setGitDir(new File(dir)).readEnvironment().findGitDir().build()

      LocalRepoContainer(repoDir, repo)
    }
  }

  // Only used by initial scan
  private def scanResult(repo: Repository, localDir: String) = Future {
    val remotes = repo.getRemoteNames().asScala.toSet.map { n =>
      repo.getConfig().getString("remote", n, "url")
    }
    val valid = repo.getObjectDatabase().exists()

    GitScanResult(
      localDir,
      valid, // not needed?
      remotes)
  }

  def scanGitDirectory(dataDirectory: String): Source[GitScanResult, Any] = {
    // recursive source mad dangerous?
    def checkForGitDirs(checkDir: String): Source[String, Any] = {
      val subdirs: List[String] = new File(checkDir).listFiles
        .filter(_.isDirectory)
        .map(_.getAbsolutePath())
        .toList

      if (subdirs.exists(_.endsWith((".git")))) {
        Source(checkDir :: Nil)
      } else {
        Source(subdirs).flatMapConcat(checkForGitDirs)
      }
    }

    checkForGitDirs(dataDirectory).mapAsync(2) { localDir =>
      for {
        r <- getGitRepoInner(localDir)
        res <- scanResult(
          r.repo,
          localDir)
        _ = r.close()
      } yield {
        res
      }
    }
  }
}
