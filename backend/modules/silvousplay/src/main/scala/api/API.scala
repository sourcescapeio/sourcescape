package silvousplay.api

import play.api._
import play.api.mvc._
import play.api.mvc.Results._

import play.api.data._
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Failure

import akka.stream.scaladsl.{ Source, Sink, Flow }

abstract class BaseAPIException(message: String) extends Exception(message) {
  val status: Int

  val dto: JsValue

  lazy val result = (new Status(status))(dto)
}

case class APIExceptionWithData[T](code: String, message: String, data: T, status: Int)(implicit val writes: Writes[T]) extends BaseAPIException(message) {
  val dto = Json.obj(
    "error" -> message,
    "errorCode" -> code,
    "data" -> Json.toJson(data))

}

case class APIException(code: String, message: String, status: Int) extends BaseAPIException(message) {
  val dto = Json.obj(
    "error" -> message,
    "errorCode" -> code)
}

trait FormAPI {
  def withFormLight[T, V](form: Form[T])(f: T => V)(implicit request: Request[AnyContent]): Either[JsObject, V] = {
    form.bindFromRequest.fold(
      formWithErrors => {
        val errors = formWithErrors.errors.groupBy(_.message).map {
          case (msg, formErrors) => Json.obj(msg -> formErrors.map(_.key))
        }.foldLeft(Json.obj())(_ ++ _)

        val errorMap = Json.obj(
          "error" -> "form.invalid") ++ errors

        Left(errorMap)
      },
      item => {
        Right(f(item))
      })
  }

  def withForm[T, V](form: Form[T])(f: T => V)(implicit request: Request[AnyContent], resultable: Resultable[V]) = {
    withFormLight(form)(f).fold(
      errorMap => Future.successful(BadRequest(errorMap)),
      item => resultable.toResult(item))
  }

  def withJson[P, T](f: P => T)(implicit request: Request[JsValue], reads: Reads[P], resultable: Resultable[T]): Future[Result] = {
    request.body.validate[P] match {
      case JsSuccess(payload, _) => resultable.toResult(f(payload))
      case e: JsError            => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }
}

trait API extends InjectedController with FormAPI {

  def api[T: Resultable, V](f: Request[AnyContent] => T)(implicit ex: ExecutionContext): Action[AnyContent] = {
    api(parse.anyContent)(f)
  }

  def api[T, V](parser: BodyParser[V])(f: Request[V] => T)(implicit ex: ExecutionContext, resultable: Resultable[Future[T]]): Action[V] = {
    Action.async(parser) { request =>
      resultable.toResult {
        Future {
          f(request)
        }
      }
    }
  }

  // only dealing with out right now
  def websocket(f: RequestHeader => Future[Source[JsValue, _]])(implicit ec: ExecutionContext) = {
    WebSocket.acceptOrResult[JsValue, JsValue] { request =>
      (for {
        socket <- Future(f(request)).flatten
      } yield {
        val source = socket.merge {
          Source.tick(1.second, 20.second, Json.toJson(Map("type" -> JsString("ping"))))
        }
        val sink = Sink.ignore
        Right(Flow.fromSinkAndSource(sink, source))
      }) recoverWith {
        case e: BaseAPIException => {
          Resultable.BaseAPIExceptionIsResultable.toResult(e).map(r => Left(r))
        }
        case e: Exception => {
          Resultable.UnknownErrorIsResultable.toResult(e).map(r => Left(r))
        }
      }
    }
  }
}
