package silvousplay

import scala.util.{ Try, Success, Failure }

trait Keyable[K] {
  val name: String

  def deserializeMessage(key: String, failure: Exception) = s"unable to deserialize key $key with type $name"

  def toString(k: K): String
  def fromString(s: String): Try[K]
}

object Keyable {

  def encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
  def decode(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")

  implicit object stringIsKeyable extends Keyable[String] {
    val name = "string"

    def toString(k: String): String = encode(k)
    def fromString(s: String): Try[String] = Try(decode(s))
  }

  implicit object longIsKeyable extends Keyable[Long] {
    val name = "long"

    def toString(k: Long): String = k.toString
    def fromString(s: String): Try[Long] = Try(s.toLong)
  }

  implicit object intIsKeyable extends Keyable[Int] {
    val name = "int"

    def toString(k: Int): String = k.toString
    def fromString(s: String): Try[Int] = Try(s.toInt)
  }

  //for single key items
  implicit object unitIsKeyable extends Keyable[Unit] {
    val name = "unit"

    def toString(k: Unit): String = ""
    def fromString(s: String): Try[Unit] = Try(())
  }

  val DupleSeparator = "#" //Use something that gets URL encoded

  implicit def duplesAreKeyable[A, B](implicit k1: Keyable[A], k2: Keyable[B]) = new Keyable[(A, B)] {
    val name = s"${k1.name}.${k2.name}"

    def toString(k: (A, B)): String = {
      k1.toString(k._1) + DupleSeparator + k2.toString(k._2)
    }

    def fromString(s: String): Try[(A, B)] = {
      //for over Try[T]
      for {
        (aStr, bStr) <- Try {
          s.split(DupleSeparator) match {
            case Array(a, b) => (a, b)
            case _           => throw new Exception("unable to split duple")
          }
        }
        aDecoded <- k1.fromString(aStr)
        bDecoded <- k2.fromString(bStr)
      } yield {
        (aDecoded, bDecoded)
      }
    }
  }

  implicit def triplesAreKeyable[A, B, C](implicit k1: Keyable[A], k2: Keyable[B], k3: Keyable[C]) = new Keyable[(A, B, C)] {
    val name = s"${k1.name}.${k2.name}.${k3.name}"

    def toString(k: (A, B, C)): String = {
      k1.toString(k._1) + DupleSeparator + k2.toString(k._2) + DupleSeparator + k3.toString(k._3)
    }

    def fromString(s: String): Try[(A, B, C)] = {
      //for over Try[T]
      for {
        (aStr, bStr, cStr) <- Try {
          s.split(DupleSeparator) match {
            case Array(a, b, c) => (a, b, c)
            case _              => throw new Exception("unable to split triple")
          }
        }
        aDecoded <- k1.fromString(aStr)
        bDecoded <- k2.fromString(bStr)
        cDecoded <- k3.fromString(cStr)
      } yield {
        (aDecoded, bDecoded, cDecoded)
      }
    }
  }

}

