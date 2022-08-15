package silvousplay.api

import io.honeycomb.opentelemetry.OpenTelemetryConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.context.Context
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.Source

case class SpanContext(tracer: Tracer, span: Span) {

  def withSpan[T](name: String)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    f(this.copy(span = newSpan)).map { r =>
      newSpan.end()
      r
    }
  }

  def withSpanS[T](name: String, attrib: (String, String)*)(f: SpanContext => Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

    f(this.copy(span = newSpan)).watchTermination()((_, done) => {
      done.onComplete {
        case scala.util.Failure(_) => newSpan.end()
        case scala.util.Success(_) => newSpan.end()
      }
    })
  }

  def withSpanFS[T](name: String)(f: SpanContext => Future[Source[T, _]])(implicit ec: ExecutionContext): Future[Source[T, _]] = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    f(this.copy(span = newSpan)).map { s =>
      s.watchTermination()((_, done) => {
        done.onComplete {
          case scala.util.Failure(_) => newSpan.end()
          case scala.util.Success(_) => newSpan.end()
        }
      })
    }
  }

  def event(name: String) = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()
    newSpan.end()
  }
}

trait Telemetry {

  val honeycomb = OpenTelemetryConfiguration.builder()
    .setApiKey("5pphkm9TGhQRUFKcZZ6oRA")
    .setServiceName("SourceScape.Graph")
    .build()

  protected def withTelemetry[T](f: SpanContext => Future[T])(implicit ec: ExecutionContext) = {
    val tracer = honeycomb.getTracer("graph-query")

    val span = tracer.spanBuilder("query").startSpan() // what happens if we don't terminate a span?

    f(SpanContext(tracer, span)).map { r =>
      span.end()
      r
    }
  }
}
