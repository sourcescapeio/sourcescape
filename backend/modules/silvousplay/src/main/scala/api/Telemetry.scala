package silvousplay.api

import io.honeycomb.opentelemetry.OpenTelemetryConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.context.Context
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Flow, Source }

case class SpanContext(tracer: Tracer, span: Span) {

  def withSpanC[T](name: String, attrib: (String, String)*)(f: SpanContext => T)(implicit ec: ExecutionContext): T = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

    val r = f(this.copy(span = newSpan))
    newSpan.end()
    r
  }

  def withSpan[T](name: String, attrib: (String, String)*)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

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

  def withSpanF[T1, T2](name: String, attrib: (String, String)*)(f: SpanContext => Flow[T1, T2, _])(implicit ec: ExecutionContext): Flow[T1, T2, _] = {
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

  def event(name: String, attrib: (String, String)*) = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

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
