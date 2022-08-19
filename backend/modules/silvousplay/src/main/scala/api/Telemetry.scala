package silvousplay.api

import io.honeycomb.opentelemetry.OpenTelemetryConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.context.Context
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Flow, Source }

case class SpanContext(tracer: Tracer, span: Span) {

  private def buildSpan(name: String, attrib: (String, String)*) = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

    newSpan
  }

  /**
   * Decoupled API
   */
  def decoupledSpan[T](name: String, attrib: (String, String)*) = {
    val newSpan = buildSpan(name, attrib: _*)
    this.copy(span = newSpan)
  }

  def terminate() = this.span.end()

  def terminateFor[T](source: Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = {
    source.watchTermination()((_, done) => {
      done.onComplete {
        case scala.util.Failure(_) => this.span.end()
        case scala.util.Success(_) => this.span.end()
      }
    })
  }

  def terminateFor[T, T2](flow: Flow[T, T2, _])(implicit ec: ExecutionContext): Flow[T, T2, _] = {
    flow.watchTermination()((_, done) => {
      done.onComplete {
        case scala.util.Failure(_) => this.span.end()
        case scala.util.Success(_) => this.span.end()
      }
    })
  }

  //
  def withSpanC[T](name: String, attrib: (String, String)*)(f: SpanContext => T)(implicit ec: ExecutionContext): T = {
    val newSpan = buildSpan(name, attrib: _*)

    val r = f(this.copy(span = newSpan))
    newSpan.end()
    r
  }

  def withSpan[T](name: String, attrib: (String, String)*)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val newSpan = buildSpan(name, attrib: _*)

    f(this.copy(span = newSpan)).map { r =>
      newSpan.end()
      r
    }
  }

  def withSpanS[T](name: String, attrib: (String, String)*)(f: SpanContext => Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = {
    val newContext = decoupledSpan(name, attrib: _*)
    newContext.terminateFor(f(newContext))
  }

  def withSpanF[T1, T2](name: String, attrib: (String, String)*)(f: SpanContext => Flow[T1, T2, _])(implicit ec: ExecutionContext): Flow[T1, T2, _] = {
    val newContext = decoupledSpan(name, attrib: _*)
    newContext.terminateFor(f(newContext))
  }

  def event(name: String, attrib: (String, String)*) = {
    val newSpan = buildSpan(name, attrib: _*)
    println(name, attrib.map {
      case (k, v) => s"${k}[${v}]"
    }.mkString(" "))
    newSpan.end()
  }
}

trait Telemetry {

  val honeycomb = OpenTelemetryConfiguration.builder()
    .setApiKey("5pphkm9TGhQRUFKcZZ6oRA")
    .setServiceName("SourceScape.Graph")
    .build()

  protected def getSpanContext() = {
    val tracer = honeycomb.getTracer("graph-query")

    val span = tracer.spanBuilder("query").startSpan() // what happens if we don't terminate a span?

    SpanContext(tracer, span)
  }

  protected def withTelemetry[T](f: SpanContext => Future[T])(implicit ec: ExecutionContext) = {
    val context = getSpanContext()
    f(context).map { r =>
      context.terminate()
      r
    }
  }
}
