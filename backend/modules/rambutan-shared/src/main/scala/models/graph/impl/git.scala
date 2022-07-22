package models.graph.git

import models.graph._
import models._
import models.query.GenericGraphEdgeType
import play.api.libs.json._

case class GitHead(
  repoId:   Int,
  repoName: String,
  name:     String,
  sha:      String) extends GenericNodeBuilder(
  GenericGraphNodeType.GitHead,
  List(
    GenericGraphProperty("sha", sha),
    GenericGraphProperty("head", name),
    GenericGraphProperty("repo_name", repoName),
    GenericGraphProperty("repo_id", repoId.toString())),
  idempotencyKey = Some("head::" + repoId + ":" + repoName + ":" + name))

case class GitCommit(
  repoId:   Int,
  repoName: String,
  parents:  List[String],
  sha:      String) extends GenericNodeBuilder(
  GenericGraphNodeType.GitCommit,
  List(
    GenericGraphProperty("sha", sha),
    GenericGraphProperty("repo_name", repoName),
    GenericGraphProperty("repo_id", repoId.toString())) ++ parents.map { p =>
      GenericGraphProperty("parent", p)
    },
  payload = Json.obj(
    "sha" -> sha),
  idempotencyKey = Some(GitKeys.commit(repoId, repoName, sha)))

case class CodeIndex(
  repoId:   Int,
  repoName: String,
  sha:      String,
  indexId:  Int) extends GenericNodeBuilder(
  GenericGraphNodeType.CodeIndex,
  List(
    GenericGraphProperty("sha", sha),
    GenericGraphProperty("repo_name", repoName),
    GenericGraphProperty("repo_id", repoId.toString()),
    GenericGraphProperty("index_id", indexId.toString())),
  idempotencyKey = Some("index::" + repoId + ":" + sha + ":" + indexId))

object GitKeys {
  def commit(repoId: Int, repoName: String, sha: String) = {
    s"commit::${repoId}:${repoName}:${sha}"
  }
}

object GitWriter {

  def materializeIndex(index: RepoSHAIndex) = {
    val repoId = index.repoId
    val repoName = index.repoName
    val indexNode = CodeIndex(repoId, repoName, index.sha, index.id)
    val commitNode = GitCommit(repoId, repoName, Nil, index.sha)
    val commitEdge = CreateEdge(commitNode, indexNode, GenericEdgeType.GitCommitIndex).edge()

    ExpressionWrapper(
      indexNode,
      Nil,
      Nil,
      commitEdge :: Nil)
  }

  def materializeBranch(branch: String, sha: String, repo: GenericRepo) = {
    val headNode = GitHead(repo.repoId, repo.repoName, branch, sha)
    val commitNode = GitCommit(repo.repoId, repo.repoName, Nil, sha)
    val commitEdge = CreateEdge(headNode, commitNode, GenericEdgeType.GitHeadCommit).edge()

    ExpressionWrapper(
      headNode,
      Nil,
      Nil,
      commitEdge :: Nil)
  }

  def materializeCommit(sha: RepoSHA, repo: GenericRepo) = {
    val commitNode = GitCommit(repo.repoId, repo.repoName, sha.parents, sha.sha)

    val parentEdges = sha.parents.map { parentSHA =>
      val parentNode = GitCommit(repo.repoId, repo.repoName, Nil, parentSHA)

      CreateEdge(commitNode, parentNode, GenericEdgeType.GitCommitParent).edge(
        "child" -> commitNode.sha,
        "parent" -> parentNode.sha)
    }

    ExpressionWrapper(
      commitNode,
      Nil,
      Nil,
      parentEdges)
  }
}
