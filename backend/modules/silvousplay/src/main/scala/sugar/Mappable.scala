package silvousplay

import scala.language.higherKinds

trait Mappable[R[_]] {
  def map[A, B](in: R[A])(f: A => B): R[B]
}

trait MappableHelpers {
  type Mappable[T[_]] = silvousplay.Mappable[T]

  implicit object optionIsMappable extends Mappable[Option] {
    def map[A, B](in: Option[A])(f: A => B): Option[B] = {
      in.map(f)
    }
  }

  implicit object listIsMappable extends Mappable[List] {
    def map[A, B](in: List[A])(f: A => B): List[B] = {
      in.map(f)
    }
  }
}
