package silvousplay.api

import javax.inject._
import io.honeycomb.opentelemetry.OpenTelemetryConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.context.Context
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.{ Flow, Source }

trait SpanContext {
  val traceId: String

  def decoupledSpan[T](name: String, attrib: (String, String)*): SpanContext
  def terminate(): Unit
  def terminateFor[T](source: Source[T, _])(implicit ec: ExecutionContext): Source[T, _]
  def terminateFor[T, T2](flow: Flow[T, T2, _])(implicit ec: ExecutionContext): Flow[T, T2, _]

  def withSpanC[T](name: String, attrib: (String, String)*)(f: SpanContext => T)(implicit ec: ExecutionContext): T
  def withSpan[T](name: String, attrib: (String, String)*)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T]
  def withSpanS[T](name: String, attrib: (String, String)*)(f: SpanContext => Source[T, _])(implicit ec: ExecutionContext): Source[T, _]
  def withSpanF[T1, T2](name: String, attrib: (String, String)*)(f: SpanContext => Flow[T1, T2, _])(implicit ec: ExecutionContext): Flow[T1, T2, _]

  def event(name: String, attrib: (String, String)*): Unit
}

case class SpanContextImpl(tracer: Tracer, span: Span, doPrint: Boolean) extends SpanContext {

  lazy val traceId = span.getSpanContext().getTraceId()

  private def buildSpan(name: String, attrib: (String, String)*) = {
    val newSpan = this.tracer.spanBuilder(name).setParent(
      Context.current().`with`(this.span)).startSpan()

    attrib.foreach {
      case (k, v) => newSpan.setAttribute(k, v)
    }

    if (doPrint) {
      println(name, attrib.map {
        case (k, v) => s"${k}[${v}]"
      }.mkString(" "))
    }

    newSpan
  }

  /**
   * Decoupled API
   */
  override def decoupledSpan[T](name: String, attrib: (String, String)*) = {
    val newSpan = buildSpan(name, attrib: _*)
    this.copy(span = newSpan)
  }

  override def terminate() = this.span.end()

  override def terminateFor[T](source: Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = {
    source.watchTermination()((_, done) => {
      done.onComplete {
        case scala.util.Failure(_) => this.span.end()
        case scala.util.Success(_) => this.span.end()
      }
    })
  }

  override def terminateFor[T, T2](flow: Flow[T, T2, _])(implicit ec: ExecutionContext): Flow[T, T2, _] = {
    flow.watchTermination()((_, done) => {
      done.onComplete {
        case scala.util.Failure(_) => this.span.end()
        case scala.util.Success(_) => this.span.end()
      }
    })
  }

  //
  override def withSpanC[T](name: String, attrib: (String, String)*)(f: SpanContext => T)(implicit ec: ExecutionContext): T = {
    val newSpan = buildSpan(name, attrib: _*)

    val r = f(this.copy(span = newSpan))
    newSpan.end()
    r
  }

  override def withSpan[T](name: String, attrib: (String, String)*)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val newSpan = buildSpan(name, attrib: _*)

    f(this.copy(span = newSpan)).map { r =>
      newSpan.end()
      r
    }
  }

  override def withSpanS[T](name: String, attrib: (String, String)*)(f: SpanContext => Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = {
    val newContext = decoupledSpan(name, attrib: _*)
    newContext.terminateFor(f(newContext))
  }

  override def withSpanF[T1, T2](name: String, attrib: (String, String)*)(f: SpanContext => Flow[T1, T2, _])(implicit ec: ExecutionContext): Flow[T1, T2, _] = {
    val newContext = decoupledSpan(name, attrib: _*)
    newContext.terminateFor(f(newContext))
  }

  override def event(name: String, attrib: (String, String)*) = {
    val newSpan = buildSpan(name, attrib: _*)
    newSpan.end()
  }
}

case object NoopSpanContext extends SpanContext {
  val traceId = ""

  def decoupledSpan[T](name: String, attrib: (String, String)*): SpanContext = {
    this
  }
  def terminate(): Unit = {}
  def terminateFor[T](source: Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = source
  def terminateFor[T, T2](flow: Flow[T, T2, _])(implicit ec: ExecutionContext): Flow[T, T2, _] = flow

  def withSpanC[T](name: String, attrib: (String, String)*)(f: SpanContext => T)(implicit ec: ExecutionContext): T = f(this)
  def withSpan[T](name: String, attrib: (String, String)*)(f: SpanContext => Future[T])(implicit ec: ExecutionContext): Future[T] = f(this)
  def withSpanS[T](name: String, attrib: (String, String)*)(f: SpanContext => Source[T, _])(implicit ec: ExecutionContext): Source[T, _] = f(this)
  def withSpanF[T1, T2](name: String, attrib: (String, String)*)(f: SpanContext => Flow[T1, T2, _])(implicit ec: ExecutionContext): Flow[T1, T2, _] = f(this)

  def event(name: String, attrib: (String, String)*): Unit = {
    println(name, attrib.map {
      case (k, v) => s"${k}[${v}]"
    }.mkString(" "))
  }
}

@Singleton
class TelemetryService @Inject() (
  val configuration: play.api.Configuration) {
  lazy val honeycomb = {
    val honeycombKey = configuration.getOptional[String]("honeycomb.key")

    honeycombKey match {
      case Some(v) => {
        Option {
          OpenTelemetryConfiguration.builder()
            .setApiKey(v)
            .setServiceName("SourceScape.Graph")
            .build()
        }
      }
      case _ => {
        None
      }
    }
  }

  private def getSpanContext(doPrint: Boolean) = {
    honeycomb match {
      case Some(h) => {
        val tracer = h.getTracer("graph-query")

        val span = tracer.spanBuilder("query").startSpan()

        SpanContextImpl(tracer, span, doPrint)
      }
      case None => {
        NoopSpanContext
      }
    }
  }

  def withTelemetry[T](f: SpanContext => Future[T])(implicit ec: ExecutionContext) = {
    val context = getSpanContext(doPrint = false)
    f(context).map { r =>
      context.terminate()
      r
    }
  }
}
