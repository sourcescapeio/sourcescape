package silvousplay

import scala.concurrent.Future
import slick.dbio._
import akka.stream.scaladsl.Source
import play.api.libs.json._
import akka.util.ByteString

trait HasZero[T] {
  def zero: T
}

trait EmptyableHelpers {
  type HasZero[T] = silvousplay.HasZero[T]

  implicit def mapHasZero[K, V] = new HasZero[Map[K, V]] {
    val zero = Map.empty[K, V]
  }

  implicit def seqHasZero[V] = new HasZero[Seq[V]] {
    val zero = Nil
  }

  implicit def optionHasZero[V] = new HasZero[Option[V]] {
    val zero = None
  }

  implicit def listHasZero[V] = new HasZero[List[V]] {
    val zero = Nil
  }

  implicit def setHasZero[V] = new HasZero[Set[V]] {
    val zero = Set.empty[V]
  }

  implicit object unitHasZero extends HasZero[Unit] {
    val zero = ()
  }

  implicit object stringHasZero extends HasZero[String] {
    val zero = ""
  }

  implicit object byteStringHasZero extends HasZero[ByteString] {
    val zero = ByteString.empty
  }

  implicit object jsObjectHasZero extends HasZero[JsObject] {
    val zero = Json.obj()
  }

  implicit def futureOfEmptyableHasZero[V](implicit sub: HasZero[V]) = new HasZero[Future[V]] {
    val zero = Future.successful(sub.zero)
  }

  implicit def dbioOfEmptyableHasZero[V, E <: Effect](implicit sub: silvousplay.HasZero[V]) = new silvousplay.HasZero[DBIOAction[V, NoStream, E]] {
    val zero = DBIO.successful(sub.zero)
  }

  implicit def sourceHasZero[V] = new silvousplay.HasZero[Source[V, Any]] {
    val zero = Source.empty[V]
  }

  // Functional helpers
  final def withFlag[T](flag: Boolean)(work: => T)(implicit zero: HasZero[T]): T = {
    flag match {
      case true  => work
      case false => zero.zero
    }
  }

  final def ifNonEmpty[T](m: Iterable[Any]*)(f: => T)(implicit zero: HasZero[T]): T = {
    withFlag(!m.exists(_.isEmpty))(f)
  }

  final def ifEmpty[T](m: Iterable[Any]*)(f: => T)(implicit zero: HasZero[T]): T = {
    withFlag(m.forall(_.isEmpty))(f)
  }

  final def withDefined[T, T2](m: Option[T])(f: T => T2)(implicit zero: HasZero[T2]): T2 = {
    m.map(f).getOrElse(zero.zero)
  }
}
