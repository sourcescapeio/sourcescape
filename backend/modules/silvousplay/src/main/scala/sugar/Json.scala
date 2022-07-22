package silvousplay

import play.api.libs.json._
import play.api.data.validation.ValidationError
import scala.concurrent.duration._

trait JsonHelpers {

  /**
   * Extra implicit formats
   */
  implicit val durationFormat: Format[FiniteDuration] = implicitly[Format[Long]].asOther(
    { d => d.toMillis },
    { l => l.millis })

  /**
   * Decorated Play JSON parsers
   */
  implicit class RichJsonWrites[T](val inner: Writes[T]) {
    def invertedMap[A](f: A => T): Writes[A] = new Writes[A] {
      def writes(obj: A): JsValue = inner.writes(f(obj))
    }
  }

  implicit class RichJsonFormat[T](val inner: Format[T]) {
    lazy val innerCastReads: Reads[T] = inner
    lazy val innerCastWrites: Writes[T] = inner

    def withValidations(validations: (String, T => Boolean)*): Format[T] = {
      Format(innerCastReads.withValidations(validations: _*), innerCastWrites)
    }

    def asOther[V](from: V => T, to: T => V): Format[V] = {
      val adaptedWrites = new Writes[V] {
        def writes(obj: V): JsValue = innerCastWrites.writes(from(obj))
      }

      Format[V](innerCastReads.map(to), adaptedWrites)
    }
  }

  implicit class RichJsonReads[T](val inner: Reads[T]) {
    def withValidations(validations: (String, T => Boolean)*): Reads[T] = new Reads[T] {

      def reads(any: JsValue): JsResult[T] = {
        inner.reads(any) match {
          case va @ JsSuccess(raw, path) => {
            val validationErrors = validations.toList.flatMap {
              case (_, f) if f(raw) => None
              case (message, _)     => Some(ValidationError(message))
            }

            validationErrors match {
              case _ => va
              //              case ve  => JsError(Seq(path -> ve))
            }
          }
          case err @ JsError(_) => err
        }
      }
    }
  }
}
