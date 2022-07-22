package silvousplay.api

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }

trait APIHelpers {

  type API = silvousplay.api.API

  type APIException = silvousplay.api.APIException
}