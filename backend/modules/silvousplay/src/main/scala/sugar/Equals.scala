package silvousplay

import scalaz._
import scala.language.implicitConversions

// If types are equal, we can compare. Yes, this is not fully safe.
// But do you really want to define equals[A] for every case class?
trait LowPriorityEquals {
  implicit def sameEqual[T] = Equal.equalA[T]

  implicit val numericEqual = Equal.equalA[Numeric[_]]

  implicit class EqualsAdditionalOps[T](val self: T)(implicit equals: Equal[T]) {
    def =?=(other: T): Boolean = equals.equal(self, other)

    def =/=(other: T): Boolean = !equals.equal(self, other)
  }
}

trait EqualsHelpers
  extends LowPriorityEquals

