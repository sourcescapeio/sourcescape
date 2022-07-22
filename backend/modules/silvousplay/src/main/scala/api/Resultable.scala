package silvousplay.api

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.Logger
import io.sentry.Sentry
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }

abstract class Resultable[T] {
  def toResult(v: T): Future[Result]
}

object Resultable {
  implicit val UnknownErrorIsResultable: Resultable[Exception] = new Resultable[Exception] {
    override def toResult(err: Exception): Future[Result] = {
      val traceLines = err.getStackTrace().map { i =>
        s"${i.getFileName()}:${i.getLineNumber()}"
      }

      println(s"Uncaught Exception: ${err.getMessage()}")
      traceLines.foreach(println)

      Sentry.capture(err)

      val message = Json.obj("error" -> "Unknown error", "errorCode" -> "error.unknown")
      Future successful InternalServerError(message)
    }
  }

  implicit val BaseAPIExceptionIsResultable = new Resultable[BaseAPIException] {
    override def toResult(err: BaseAPIException): Future[Result] = {
      Future successful err.result
    }
  }

  implicit val ResultIsResultable: Resultable[Result] = new Resultable[Result] {
    override def toResult(res: Result): Future[Result] = Future successful res
  }

  implicit def FutureIsResultable[T](implicit r: Resultable[T]): Resultable[Future[T]] = new Resultable[Future[T]] {

    implicit val ec = ExecutionContext.Implicits.global //Directly using global context here is safe because nonblocking

    override def toResult(f: Future[T]): Future[Result] = for {
      result <- f.flatMap(r.toResult).recoverWith {
        case e: BaseAPIException => {
          BaseAPIExceptionIsResultable.toResult(e)
        }
        case e: Exception => {
          UnknownErrorIsResultable.toResult(e)
        }
      }
    } yield {
      result
    }
  }

  implicit def JsonableIsResultable[T](implicit writes: Writes[T]): Resultable[T] = new Resultable[T] {
    override def toResult(v: T): Future[Result] = Future successful Ok(Json.toJson(v))
  }

  implicit val HtmlIsResultable: Resultable[Html] = new Resultable[Html] {
    override def toResult(html: Html): Future[Result] = Future successful Ok(html)
  }

  implicit def OptionIsResultable[T](implicit r: Resultable[T]): Resultable[Option[T]] = new Resultable[Option[T]] {
    override def toResult(o: Option[T]): Future[Result] = o match {
      case Some(v) => r.toResult(v)
      case _       => Future successful NotFound(Json.obj("errorCode" -> "notfound"))
    }
  }

  implicit val UnitIsResultable: Resultable[Unit] = new Resultable[Unit] {
    override def toResult(o: Unit): Future[Result] = Future.successful(Ok(Json.obj("success" -> true)))
  }
}