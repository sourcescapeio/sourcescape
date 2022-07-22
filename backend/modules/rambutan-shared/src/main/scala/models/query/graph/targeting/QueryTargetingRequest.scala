package models.query

import silvousplay.imports._
import models.{ IndexType, ESQuery, RepoSHAIndex }
import play.api.libs.json._
import models.graph._

sealed trait QueryTargetingRequest

object QueryTargetingRequest {
  // should only be used by debug. replaced by RepoLatest for security
  case class AllLatest(fileFilter: Option[String]) extends QueryTargetingRequest

  case class RepoLatest(repoIds: List[Int], fileFilter: Option[String]) extends QueryTargetingRequest

  case class ForIndexes(indexIds: List[Int], fileFilter: Option[String]) extends QueryTargetingRequest
}
