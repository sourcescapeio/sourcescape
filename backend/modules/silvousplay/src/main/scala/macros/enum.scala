package silvousplay.macros

import play.api.libs.json._
import play.api.data.format._
import play.api.data.Forms.of
import play.api.mvc.{ QueryStringBindable, PathBindable }
import play.api.data.FormError
import slick.lifted.Isomorphism
import scala.util.Try
import scala.reflect.ClassTag
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait MacroEnum[T <: HasIdentity[I], I] {
  def extract(): Seq[T]
  def extractIdentifier(): Seq[I] = {
    extract().map(_.identifier)
  }
}

trait HasIdentity[T] {
  val identifier: T
}

trait Identifiable extends HasIdentity[String]

trait IntIdentifiable extends HasIdentity[Int]

trait HasAll[T] {
  def all: Seq[T]
}

object HasAll {
  val A = 1
  implicit def hasAll[T <: HasIdentity[_]]: HasAll[T] = macro EnumImpl.hasAll_impl[T]

  def extract[T]: List[T] = macro EnumImpl.extract_impl[T]
}

object EnumImpl {
  def extract_impl[A](c1: blackbox.Context)(implicit evidence1: c1.WeakTypeTag[A]): c1.Expr[List[A]] = {
    import c1.universe._
    import c1.universe.Flag._

    val weakType = weakTypeOf[A]
    val enclosing = c1.internal.enclosingOwner.owner
    val allMembers = enclosing.typeSignature.members.flatMap {
      case s if s.isModule => Some(s.asModule)
      case _               => None
    }
    val matchingMembers = allMembers.filter(_.typeSignature <:< weakType)
    val qualifiedNames = matchingMembers.iterator.toList.map(_.name)

    val tree = q"""
      List(
        ..$qualifiedNames
      )
    """

    c1.Expr[List[A]](tree)
  }

  def hasAll_impl[A <: HasIdentity[_]](c1: blackbox.Context)(implicit evidence1: c1.WeakTypeTag[A]): c1.Expr[HasAll[A]] = {
    import c1.universe._
    import c1.universe.Flag._

    val weakType = weakTypeOf[A]
    val symbol = weakType.typeSymbol.asClass

    val enclosing = c1.internal.enclosingOwner.owner
    val matchingMembers = enclosing.typeSignature.members.filter(_.typeSignature <:< weakType).iterator.toList
    val qualifiedNames = matchingMembers.map(i => Ident(i))

    val seqApply = Select(
      reify(Seq).tree,
      TermName("apply"))

    val innerTree = c1.Expr[Seq[A]](
      Apply(
        seqApply,
        qualifiedNames))

    reify {
      new HasAll[A] {
        val all: Seq[A] = {
          innerTree.splice
        }
      }
    }
  }
}

sealed abstract class TypedEnumeration[T <: HasIdentity[I], I](
  implicit
  iFormat:  Format[I],
  keyable:  silvousplay.Keyable[I],
  classTag: ClassTag[T],
  hasAll:   HasAll[T])
  extends MacroEnum[T, I] {

  final def extract(): Seq[T] = hasAll.all

  val all: Seq[T] = extract()

  private lazy val byIdentifier: Map[I, T] = all.map(obj => obj.identifier -> obj).toMap

  val reads: Reads[T] = iFormat.flatMap { v =>
    Reads[T] { jsValue =>
      withName(v) match {
        case Some(t) => JsSuccess(t)
        case _ =>
          val allValues = all.map(_.identifier).mkString(",")
          JsError(s"Invalid enum value $jsValue Valid values: $allValues")
      }
    }
  }

  val writes: Writes[T] = new Writes[T] {
    def writes(obj: T): JsValue = iFormat.writes(obj.identifier)
  }

  implicit val formats: Format[T] = Format(reads, writes)

  implicit val iso = new Isomorphism[T, I](
    { qm => qm.identifier },
    { id => withNameUnsafe(id) })

  implicit object isKeyable extends silvousplay.Keyable[T] {
    lazy val name = classTag.toString

    def toString(k: T): String = keyable.toString(k.identifier)
    def fromString(s: String): Try[T] = keyable.fromString(s).flatMap(i => Try(withNameUnsafe(i)))
  }

  def withName(a: I): Option[T] = byIdentifier.get(a)

  def withNameUnsafe(id: I): T = {
    withName(id).getOrElse(throw new Exception(s"failed mapping for $id. acceptable values: " + all.map(_.identifier).mkString(",")))
  }
}

abstract class Plenumeration[T <: Identifiable](implicit classTag: ClassTag[T], hasAll: HasAll[T]) extends TypedEnumeration[T, String] with silvousplay.JsonHelpers { //ewww

  implicit def mapFormats[V](implicit f: Format[V]): Format[Map[T, V]] = {
    implicitly[Format[Map[String, V]]].asOther(
      { m => m.map { case (k, v) => k.identifier -> v } },
      { mm => mm.map { case (k, v) => withNameUnsafe(k) -> v } } //we choose to fail here
    )
  }

  // So it works as a path param
  implicit val pathBinder: PathBindable[T] = new PathBindable[T] {
    override def unbind(key: String, value: T) = value.identifier

    override def bind(key: String, value: String) = withName(value) match {
      case Some(obj) => Right(obj)
      case _         => Left(s"invalid key $value")
    }
  }

  // So it works as a query param
  implicit val queryBinder: QueryStringBindable[T] = new QueryStringBindable[T] {
    override def unbind(key: String, value: T) = value.identifier

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, T]] = {
      val extracted = params.get(key).flatMap(_.headOption)
      extracted match {
        case None => None
        case Some(rawString) => withName(rawString) match {
          case Some(obj) => Some(Right(obj))
          case None      => Some(Left(s"invalid key $key"))
        }
      }
    }
  }

  // For building Play forms
  implicit val rawFormatter: Formatter[T] = new Formatter[T] {
    def bind(key: String, data: Map[String, String]) = {
      data.get(key).map(withName _) match {
        case None          => Left(Seq(FormError(key, "error.required", Nil)))
        case Some(None)    => Left(Seq(FormError(key, "enum.invalid", List(all.map(_.identifier)))))
        case Some(Some(a)) => Right(a)
      }
    }

    def unbind(k: String, value: T) = Map(k -> value.identifier)
  }

  val form = of[T](rawFormatter)
}

abstract class IntPlenumeration[T <: IntIdentifiable](implicit classTag: ClassTag[T], hasAll: HasAll[T]) extends TypedEnumeration[T, Int] {
  implicit val rawFormatter: Formatter[T] = new Formatter[T] {
    def bind(key: String, data: Map[String, String]) = {
      data.get(key).flatMap(i => Try(i.toInt).toOption).map(withName _) match {
        case None          => Left(Seq(FormError(key, "error.required", Nil)))
        case Some(None)    => Left(Seq(FormError(key, "enum.invalid", List(all.map(_.identifier)))))
        case Some(Some(a)) => Right(a)
      }
    }

    def unbind(k: String, value: T) = Map(k -> value.identifier.toString)
  }
}

trait EnumHelpers {

  type Identifiable = silvousplay.macros.Identifiable

  type IntIdentifiable = silvousplay.macros.IntIdentifiable

  type Plenumeration[T <: Identifiable] = silvousplay.macros.Plenumeration[T]

  type IntPlenumeration[T <: IntIdentifiable] = silvousplay.macros.IntPlenumeration[T]

  val Enums = silvousplay.macros.HasAll

  implicit def identifiableMapWrites[T <: Identifiable, V](implicit fmtv: Writes[V]): OWrites[Map[T, V]] = OWrites[Map[T, V]] { ts =>
    JsObject(ts.map { case (k, v) => (k.identifier, Json.toJson(v)(fmtv)) }.toList)
  }
}
