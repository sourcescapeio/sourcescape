package services

import models._
import scala.concurrent.{ ExecutionContext, Future }

trait ScanService {

  def initialScan(orgId: Int): Future[Unit]

}
