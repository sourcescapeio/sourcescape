package models

import silvousplay.api.APIException
import play.api.libs.json._
import play.api.libs.ws._

case class SkipWork(reason: String) extends Exception(s"skipped because ${reason}")

case class ExtractionException(message: String, range: CodeRange, json: JsValue) extends Exception(
  s"${message} [L${range.start.line}:${range.start.column}-L${range.end.line}:${range.end.column}] ${json}")

case class LinkerException(path: String, message: String) extends Exception(s"Linker exception for path ${path}: ${message}")

object Errors {
  def streamError(message: String): APIException = {
    APIException("stream", message, 500)
  }

  def requestError(response: WSResponse): APIException = {
    APIException("request", response.body, response.status)
  }

  def requestError(response: JsValue): APIException = {
    APIException("request", Json.stringify(response), 500)
  }

  def requestError(response: String): APIException = {
    APIException("request", response, 500)
  }

  /**
   * Front facing
   */
  def notFound(code: String, msg: String) = {
    APIException(code, msg, 404)
  }

  def badRequest(code: String, msg: String) = {
    APIException(code, msg, 400)
  }

  def unauthorized(code: String, msg: String) = {
    APIException(code, msg, 401)
  }

  def forbidden(code: String, msg: String) = {
    APIException(code, msg, 403)
  }
}
