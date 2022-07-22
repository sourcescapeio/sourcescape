package models

import silvousplay.imports._

import play.api.libs.json._
import scala.language.implicitConversions
import scala.language.experimental.macros

import scala.reflect.macros.blackbox.Context
import scala.concurrent.java8.FuturesConvertersImpl.P
import annotation.unchecked._
import akka.dispatch.AbstractBoundedNodeQueue.Node

/***
  * Generic JSON extractor
  */

trait ExtractorContext {
  val path: String
  val debugPath: List[String]

  def pushDebug(addPath: String): ExtractorContext // danger!
  def replaceDebug(newPath: List[String]): ExtractorContext // danger!
}

/**
 * Mapper
 */
sealed trait ExtractionMapper[T, V, R] {
  def f(a: T, b: V): R
}

sealed trait LowerPriorityImplicits {
  implicit def tupMapper[A, B]: ExtractionMapper[A, B, (A, B)] = new ExtractionMapper[A, B, (A, B)] {
    def f(a: A, b: B) = (a, b)
  }
}

sealed trait LowPriorityImplicits extends LowerPriorityImplicits {
  implicit def unitRightMapper[A]: ExtractionMapper[A, Unit, A] = new ExtractionMapper[A, Unit, A] {
    def f(a: A, u: Unit) = a
  }
}

package object extractor extends LowPriorityImplicits {
  sealed case class ExtractorError(context: ExtractorContext, message: String, js: JsValue) {
    private def debugPathStr = context.debugPath.reverse.mkString("/")

    def toException = {
      throw new Exception(s"[path:${context.path}] ${debugPathStr}\n\n${message}\n${js}")
    }
  }

  case class Language[C <: ExtractorContext](typeKey: String, r: Reads[CodeRange]) {
    def readRange(context: C, in: JsValue): CodeRange = r.reads(in) match {
      case JsSuccess(a, _) => a
      case _               => throw new Exception(s"${context.path} ${context.debugPath.mkString("/")} - could not extract code range from ${in}")
    }
  }

  protected class MappedExtractor[C <: ExtractorContext, T, V](inner: Extractor[C, T], f: (C, T, JsValue) => Either[ExtractorError, (C, V)]) extends Extractor[C, V] {

    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = inner.checkConstraints(c, typeKey, js)

    def extract(context: C, js: JsValue): Either[ExtractorError, (C, V)] = {
      for {
        item <- inner.extract(context, js)
        res <- f(item._1, item._2, js)
      } yield {
        res
      }
    }
  }

  /**
   * Extractor traits
   */
  sealed trait Extractor[C <: ExtractorContext, +T] {
    def extract(context: C, js: JsValue): Either[ExtractorError, (C, T)]

    def map[V](f: (C, T, JsValue) => Either[ExtractorError, (C, V)]): Extractor[C, V] = {
      new MappedExtractor(this, f)
    }

    def mapValue[V](f: T => V): Extractor[C, V] = {
      map { (c, t, _) =>
        Right((c, f(t)))
      }
    }

    // assumes struct (has code range)
    def mapBoth[V](f: (C, CodeRange, T) => (C, V))(implicit lang: Language[C]): Extractor[C, V] = {
      map { (c, t, js) =>
        val codeRange = lang.readRange(c, js)
        Right(f(c, codeRange, t))
      }
    }

    def mapExtraction[V](f: (C, CodeRange, T) => V)(implicit lang: Language[C]): Extractor[C, V] = {
      mapBoth { (c, r, t) =>
        (c, f(c, r, t))
      }
    }

    def mapContext(f: (C, T) => C)(implicit lang: Language[C]): Extractor[C, T] = {
      mapBoth { (c, r, t) =>
        (f(c, t), t)
      }
    }

    // Note: this is kind of like a flatMap in case we want to make this monadic
    def ~[V, R](other: => Extractor[C, V])(implicit mapper: ExtractionMapper[T @uncheckedVariance, V, R]): Extractor[C, R] = {
      new CombineExtractor(this, other, mapper)
    }

    /**
     * Chaining
     */
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean

    def or[T2](other: => Extractor[C, T2])(implicit language: Language[C]) = {
      new EitherExtractor[C, T, T2](this, other, language.typeKey)
    }

    def |[T0 >: T](other: => Extractor[C, T0])(implicit language: Language[C]): ChainedExtractor[C, T0] = {
      val base = new ChainedExtractorCons(this, new ChainedExtractorNil[C, T](language.typeKey), language.typeKey)
      base.splice(other)
    }
  }

  protected class CombineExtractor[C <: ExtractorContext, T, V, R](from: Extractor[C, T], other: => Extractor[C, V], mapper: ExtractionMapper[T, V, R]) extends Extractor[C, R] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      from.checkConstraints(c, typeKey, js) // only look at root
    }

    def extract(u: C, js: JsValue) = {
      // Either[T]
      for {
        e1 <- from.extract(u, js)
        (c1, r1) = e1
        baseDebugPath = from match {
          case n: NodeExtractor[_, _] => c1.debugPath
          case _                      => u.debugPath
        }
        e2 <- other.extract(
          c1.replaceDebug(baseDebugPath).asInstanceOf[C],
          js)
        (c2, r2) = e2
      } yield {
        val cleanedContext = c2.replaceDebug(baseDebugPath).asInstanceOf[C]
        (cleanedContext, mapper.f(r1, r2))
      }
    }
  }

  // This is core extractor, corresponds to a Node in a Parse Tree
  sealed trait NodeExtractor[C <: ExtractorContext, T] extends Extractor[C, T] {
    val `type`: String

    val constraints: List[TupleExtractor[C, Boolean]]

    override def checkConstraints(c: C, typeKey: String, js: JsValue) = {
      val typeMatch = (js \ typeKey).asOpt[String] match {
        case Some(t) if t =?= `type` => true
        case _                       => false
      }

      val constraintsMatch = constraints.forall { cons =>
        cons.extract(c, js) match {
          case Right((_, true)) => true
          case _                => false
        }
      }

      typeMatch && constraintsMatch
    }
  }

  /**
   * Composite Extractors
   */
  sealed trait ChainedExtractor[C <: ExtractorContext, +T] extends Extractor[C, T] {
    override def |[T0 >: T](other: => Extractor[C, T0])(implicit context: Language[C]) = {
      splice(other)
    }

    def splice[T0 >: T](other: Extractor[C, T0])(implicit context: Language[C]): ChainedExtractor[C, T0]
  }

  sealed class ChainedExtractorNil[C <: ExtractorContext, +T](typeKey: String) extends ChainedExtractor[C, T] {

    override def checkConstraints(c: C, typeKey: String, js: JsValue) = {
      false
    }

    def extract(c: C, js: JsValue) = {
      val typeValue = scala.util.Try {
        (js \ typeKey).as[String]
      }.toOption.getOrElse("...")

      Left(ExtractorError(c, s"Could not get extractor for ${typeValue}", js))
    }

    def splice[T0 >: T](other: Extractor[C, T0])(implicit language: Language[C]) = {
      new ChainedExtractorCons[C, T0](other, new ChainedExtractorNil[C, T0](language.typeKey), language.typeKey)
    }
  }

  sealed class ChainedExtractorCons[C <: ExtractorContext, +T](head: => Extractor[C, T], tail: ChainedExtractor[C, T], typeKey: String) extends ChainedExtractor[C, T] {

    override def checkConstraints(c: C, typeKey: String, js: JsValue) = {
      head.checkConstraints(c, typeKey, js) || tail.checkConstraints(c, typeKey, js)
    }

    def extract(c: C, js: JsValue) = {
      head.checkConstraints(c, typeKey, js) match {
        case true  => head.extract(c, js)
        case false => tail.extract(c, js)
      }
    }

    def splice[T0 >: T](other: Extractor[C, T0])(implicit context: Language[C]) = {
      new ChainedExtractorCons[C, T0](
        this.head.asInstanceOf[Extractor[C, T0]],
        this.tail.splice(other), context.typeKey)
    }
  }

  sealed class EitherExtractor[C <: ExtractorContext, +T1, +T2](a: => Extractor[C, T1], b: => Extractor[C, T2], typeKey: String) extends Extractor[C, Either[T1, T2]] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      a.checkConstraints(c, typeKey, js) || b.checkConstraints(c, typeKey, js)
    }

    def extract(context: C, js: JsValue) = {
      a.checkConstraints(context, typeKey, js) match {
        case true => a.extract(context, js).map {
          case (c, r) => (c, Left(r))
        }
        case false => b.extract(context, js).map {
          case (c, r) => (c, Right(r))
        }
      }
    }
  }

  sealed class TryEitherExtractor[C <: ExtractorContext, T1, T2](a: => Extractor[C, T1], b: => Extractor[C, T2]) extends Extractor[C, Either[T1, T2]] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      // TODO: this is not great
      // tryEither(a, b) | c will fail generally
      a.checkConstraints(c, typeKey, js) || b.checkConstraints(c, typeKey, js)
    }

    def extract(context: C, js: JsValue) = {
      a.extract(context, js) match {
        case Left(err) => {
          b.extract(context, js) match {
            case Right((_, r)) => Right {
              (context, Right(r))
            }
            case Left(err2) => Left {
              err.copy(
                message = s"${err.message}\n\n${err2.message}",
                js = Json.toJson(List(err.js, err2.js))) // FML for this hack
            }
          }
        }
        case Right((_, r)) => Right((context, Left(r)))
      }
    }
  }

  sealed class OptionalExtractor[C <: ExtractorContext, T](sub: => Extractor[C, T]) extends Extractor[C, Option[T]] {
    // support opt(blah) | test << expected: return test if either null or blah fails
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      js match {
        case JsNull => false
        case _      => sub.checkConstraints(c, typeKey, js)
      }
    }

    def extract(context: C, js: JsValue) = {
      js match {
        case JsNull => Right((context, None))
        case _ => sub.extract(context, js).map {
          case (c, item) => (c, Some(item))
        }
      }
    }
  }

  sealed class ListExtractor[C <: ExtractorContext, T](sub: => Extractor[C, T]) extends Extractor[C, List[T]] {
    // as long as it's a list, accept
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      js match {
        case JsArray(_) => true
        case _          => false
      }
    }

    def extract(context: C, js: JsValue) = js match {
      case JsArray(items) => {
        val extracted = items.zipWithIndex.map {
          case (i, idx) => {
            val contextWithDebug = context.pushDebug(s"[${idx}]").asInstanceOf[C]
            sub.extract(contextWithDebug, i)
          }
        }
        val maybeError = extracted.find {
          case Left(_) => true
          case _       => false
        }
        maybeError match {
          case Some(Left(e)) => Left(e)
          case _ => Right {
            (
              context,
              extracted.toList.flatMap {
                case Right((c, r)) => Some(r)
                case _             => None
              })
          }
        }
      }
      case _ => Left(ExtractorError(context, "Expected array", js))
    }
  }

  sealed class SequenceExtractor[C <: ExtractorContext, T](sub: => Extractor[C, T]) extends Extractor[C, List[T]] {
    // as long as it's a list, accept
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      js match {
        case JsArray(_) => true
        case _          => false
      }
    }

    // recurse
    @scala.annotation.tailrec
    private def extractRecur(context: C, acc: List[T], items: List[JsValue], idx: Int): Either[ExtractorError, (C, List[T])] = {
      items match {
        case head :: rest => {
          val withDebug = context.pushDebug(s"[${idx}]").asInstanceOf[C]
          sub.extract(withDebug, head) match {
            case Right((nextC, item)) => {
              val cleanedDebug = nextC.replaceDebug(context.debugPath).asInstanceOf[C]
              extractRecur(cleanedDebug, item :: acc, rest, idx + 1)
            }
            case Left(err) => Left(err)
          }
        }
        case Nil => Right((context, acc.reverse)) // List prepend is more efficient. We reverse at the very end.
      }
    }

    def extract(context: C, js: JsValue) = js match {
      case JsArray(items) => {
        extractRecur(context, List.empty[T], items.toList, 0)
      }
      case _ => Left(ExtractorError(context, "Expected array", js))
    }
  }

  /**
   * Primitives
   */
  sealed case class RootTupleExtractor[C <: ExtractorContext](typeIn: String, typeValidator: Extractor[C, Unit], constraints: List[TupleExtractor[C, Boolean]]) extends NodeExtractor[C, Unit] {
    val `type` = typeIn

    // adds constraint
    def ~~(other: TupleExtractor[C, Boolean]) = this.copy(constraints = other :: constraints)

    def extract(c: C, js: JsValue) = {
      val baseDebug = c.pushDebug(typeIn).asInstanceOf[C]
      typeValidator.extract(baseDebug, js) map {
        case (newC, v) => {
          (newC.replaceDebug(baseDebug.debugPath).asInstanceOf[C], v)
        }
      }
    }
  }

  sealed class TupleExtractor[C <: ExtractorContext, T](val key: String, sub: => Extractor[C, T]) extends Extractor[C, T] {
    // single
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      true
    }

    def extract(c: C, js: JsValue) = (js \ key).asOpt[JsValue] match {
      case Some(innerJs) => {
        val withDebug = c.pushDebug(s"{${key}}").asInstanceOf[C]
        sub.extract(withDebug, innerJs)
      }
      case _ => Left(ExtractorError(c, s"Invalid key in tuple ${key}", js))
    }
  }

  sealed class RestExtractor[C <: ExtractorContext, VV, VR](inner: Extractor[C, VV], skipIn: Int, rest: Extractor[C, VR]) extends OrderedExtractor[C, (VV, VR)](100) {
    // def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
    //   true
    // }

    // calculate dynamic skip?

    def extract(context: C, js: JsValue) = js.asOpt[List[JsValue]] match {
      case Some(fullList) => {
        for {
          iResult <- inner.extract(context, Json.toJson(fullList))
          rResult <- rest.extract(context, Json.toJson(fullList.drop(skip)))
        } yield {
          (context, (iResult._2, rResult._2))
        }
      }
      case _ => Left(ExtractorError(context, s"Invalid list", js))
    }
  }

  sealed abstract class OrderedExtractor[C <: ExtractorContext, V](val skip: Int) extends Extractor[C, V] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      true
    }

    def and[V2](other: Extractor[C, V2]) = new RestExtractor(this, skip, other)

    protected def filledArray(array: List[JsValue]) = {
      val taken = array.take(skip)
      val filled = List.fill(skip - taken.length)(JsNull)

      taken ++ filled
    }
  }

  sealed class OrderedExtractor1[C <: ExtractorContext, T1](a: Extractor[C, T1]) extends OrderedExtractor[C, T1](skip = 1) {
    def extract(context: C, js: JsValue) = js.asOpt[Array[JsValue]] match {
      case Some(array) => {
        val aJs = array.headOption.getOrElse(JsNull)
        val withDebugA = context.pushDebug(s"[0]").asInstanceOf[C]
        for {
          aResult <- a.extract(withDebugA, aJs)
        } yield {
          (
            aResult._1.replaceDebug(context.debugPath).asInstanceOf[C],
            aResult._2)
        }
      }
      case _ => Left(ExtractorError(context, s"Invalid list", js))
    }
  }

  sealed case class OrderedExtractor2[C <: ExtractorContext, T1, T2](a: Extractor[C, T1], b: Extractor[C, T2]) extends OrderedExtractor[C, (T1, T2)](skip = 2) {
    def extract(context: C, js: JsValue) = js.asOpt[List[JsValue]] match {
      case Some(array) => {
        val List(aJs, bJs) = filledArray(array)

        val baseDebug = context.debugPath
        val withDebugA = context.pushDebug(s"[0]").asInstanceOf[C]
        for {
          aResult <- a.extract(withDebugA, aJs)
          withDebugB = aResult._1.replaceDebug(baseDebug).pushDebug(s"[1]").asInstanceOf[C]
          bResult <- b.extract(withDebugB, bJs)
        } yield {
          (
            bResult._1.replaceDebug(context.debugPath).asInstanceOf[C],
            (aResult._2, bResult._2))
        }
      }
      case _ => Left(ExtractorError(context, s"Invalid list", js))
    }
  }

  sealed case class OrderedExtractor3[C <: ExtractorContext, T1, T2, T3](a: Extractor[C, T1], b: Extractor[C, T2], c: Extractor[C, T3]) extends OrderedExtractor[C, (T1, T2, T3)](skip = 3) {
    def extract(context: C, js: JsValue) = js.asOpt[List[JsValue]] match {
      case Some(array) => {
        val List(aJs, bJs, cJs) = filledArray(array)

        val baseDebug = context.debugPath
        val withDebugA = context.pushDebug(s"[0]").asInstanceOf[C]
        for {
          aResult <- a.extract(withDebugA, aJs)
          withDebugB = aResult._1.replaceDebug(baseDebug).pushDebug(s"[1]").asInstanceOf[C]
          bResult <- b.extract(withDebugB, bJs)
          withDebugC = bResult._1.replaceDebug(baseDebug).pushDebug(s"[2]").asInstanceOf[C]
          cResult <- c.extract(withDebugC, cJs)
        } yield {
          (
            cResult._1.replaceDebug(context.debugPath).asInstanceOf[C],
            (aResult._2, bResult._2, cResult._2))
        }
      }
      case _ => Left(ExtractorError(context, s"Invalid list", js))
    }
  }

  sealed case class OrderedExtractor4[C <: ExtractorContext, T1, T2, T3, T4](a: Extractor[C, T1], b: Extractor[C, T2], c: Extractor[C, T3], d: Extractor[C, T4]) extends OrderedExtractor[C, (T1, T2, T3, T4)](skip = 4) {
    def extract(context: C, js: JsValue) = js.asOpt[List[JsValue]] match {
      case Some(array) => {
        val List(aJs, bJs, cJs, dJs) = filledArray(array)
        val baseDebug = context.debugPath
        val withDebugA = context.pushDebug(s"[0]").asInstanceOf[C]
        for {
          aResult <- a.extract(withDebugA, aJs)
          withDebugB = aResult._1.replaceDebug(baseDebug).pushDebug(s"[1]").asInstanceOf[C]
          bResult <- b.extract(withDebugB, bJs)
          withDebugC = bResult._1.replaceDebug(baseDebug).pushDebug(s"[2]").asInstanceOf[C]
          cResult <- c.extract(withDebugC, cJs)
          withDebugD = cResult._1.replaceDebug(baseDebug).pushDebug(s"[3]").asInstanceOf[C]
          dResult <- d.extract(withDebugD, dJs)
        } yield {
          (
            dResult._1.replaceDebug(context.debugPath).asInstanceOf[C],
            (aResult._2, bResult._2, cResult._2, dResult._2))
        }
      }
      case _ => Left(ExtractorError(context, s"Invalid list", js))
    }
  }

  import scala.reflect.runtime.universe._
  sealed class JsExtractor[C <: ExtractorContext, B: TypeTag]()(implicit reads: Reads[B]) extends Extractor[C, B] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      true
    }

    def extract(u: C, js: JsValue) = js.asOpt[B] match {
      case Some(b) => Right((u, b))
      case _       => Left(ExtractorError(u, s"Invalid extraction for ${typeOf[B].typeSymbol}", js))
    }
  }

  sealed class LiteralExtractor[C <: ExtractorContext, T](valid: T)(implicit reads: Reads[T]) extends Extractor[C, Unit] {
    def checkConstraints(c: C, typeKey: String, js: JsValue): Boolean = {
      true
    }

    def extract(u: C, js: JsValue) = Json.fromJson[T](js) match {
      case JsSuccess(v, _) if v =?= valid => Right((u, ()))
      case _                              => Left(ExtractorError(u, s"Failed to validate literal ${valid}", js))
    }
  }

  /**
   * Literal validator sugar
   */
  // do we want this?
  // sealed case class LiteralValidator[T](valid: T)(implicit reads: Reads[T]) {
  //   def validate[C](implicit context: Language[C]) = LiteralExtractor[C, T](valid)
  // }
  // implicit def strToLiteralStrValidator(item: String) = LiteralValidator(item)

  /**
   * Public API
   */
  object Extractor {
    def bool[C <: ExtractorContext](implicit context: Language[C]) = new JsExtractor[C, Boolean]()
    def str[C <: ExtractorContext](implicit context: Language[C]) = new JsExtractor[C, String]()
    def js[C <: ExtractorContext](implicit context: Language[C]) = new JsExtractor[C, JsValue]()
    def enum[C <: ExtractorContext](items: String*)(implicit context: Language[C]) = new JsExtractor[C, String]().map {
      case (context, item, _) if items.contains(item) => Right((context, item))
      case (context, item, _)                         => Left(ExtractorError(context, s"Invalid enum value ${item}. Valid values: ${items}", JsString(item)))
    }
  }

  object Constraints {
    def str[C <: ExtractorContext](valid: String)(implicit context: Language[C]): Extractor[C, Boolean] = {
      Extractor.str[C].mapValue(_ =?= valid)
    }
  }

  /**
   * Top level
   */
  // def array[] << need or want a parallel extractor?

  def sequence[C <: ExtractorContext, T](extractor: => Extractor[C, T]) = {
    new SequenceExtractor(extractor)
  }

  def list[C <: ExtractorContext, T](extractor: => Extractor[C, T]) = {
    new ListExtractor(extractor)
  }

  def opt[C <: ExtractorContext, T](extractor: => Extractor[C, T]) = {
    new OptionalExtractor(extractor)
  }

  def either[C <: ExtractorContext, T1, T2](extractor1: Extractor[C, T1], extractor2: Extractor[C, T2]) = {
    new TryEitherExtractor(extractor1, extractor2)
  }

  def constraint[C <: ExtractorContext](item: (String, Extractor[C, Boolean])) = {
    new TupleExtractor(item._1, item._2)
  }

  def node[C <: ExtractorContext](`type`: String)(implicit context: Language[C]) = {
    RootTupleExtractor[C](`type`, new TupleExtractor(context.typeKey, new LiteralExtractor(`type`)), constraints = Nil)
  }

  def tup[C <: ExtractorContext, T](item: (String, Extractor[C, T])) = {
    new TupleExtractor(item._1, item._2)
  }

  // experimental ordered extractor
  def ordered1[C <: ExtractorContext, T1, T2, T3](a: Extractor[C, T1]) = {
    new OrderedExtractor1(a)
  }

  def ordered2[C <: ExtractorContext, T1, T2, T3](a: Extractor[C, T1], b: Extractor[C, T2]) = {
    new OrderedExtractor2(a, b)
  }

  def ordered3[C <: ExtractorContext, T1, T2, T3](a: Extractor[C, T1], b: Extractor[C, T2], c: Extractor[C, T3]) = {
    new OrderedExtractor3(a, b, c)
  }

  def ordered4[C <: ExtractorContext, T1, T2, T3, T4](a: Extractor[C, T1], b: Extractor[C, T2], c: Extractor[C, T3], d: Extractor[C, T4]) = {
    new OrderedExtractor4(a, b, c, d)
  }

  implicit def unitLeftMapper[A]: ExtractionMapper[Unit, A, A] = new ExtractionMapper[Unit, A, A] {
    def f(u: Unit, a: A) = a
  }
}
