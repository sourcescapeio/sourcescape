package silvousplay.macros

import play.api.data.Mapping
import play.api.data._
import play.api.data.Forms._
import play.api.data.format._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait FormsHelpers {
  implicit def optOf[T](implicit inner: Formatter[T]): Formatter[Option[T]] = {
    new Formatter[Option[T]] {
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[T]] = {
        data.get(key).map { _ =>
          inner.bind(key, data).fold(a => Left(a), b => Right(Some(b)))
        }.getOrElse(Right[Seq[FormError], Option[T]](None))
      }

      def unbind(k: String, value: Option[T]): Map[String, String] = {
        value.map { v =>
          inner.unbind(k, v)
        }.getOrElse(Map.empty[String, String])
      }
    }
  }

  val FormsMacro = silvousplay.macros.Forms
}

object Forms {
  def mapping[T]: Mapping[T] = macro FormsImpl.mapping_impl[T]
}

object FormsImpl {
  def mapping_impl[A](c1: blackbox.Context)(implicit evidence1: c1.WeakTypeTag[A]): c1.Expr[Mapping[A]] = {
    import c1.universe._
    import c1.universe.Flag._

    val weakType = weakTypeOf[A]
    //extract methods
    // println(weakType.decls)
    val decls = weakType.decls.flatMap {
      case x: MethodSymbol if x.isPublic && x.isGetter => {
        val stringName = s"""${x.name}"""
        val returnType = x.returnType
        Some(q""" $stringName -> play.api.data.Forms.of[$returnType]""")
      }
      case _ => None
    }

    val tree = q"""
      play.api.data.Forms.mapping(
        ..${decls}
      )(${weakType.typeSymbol.companion}.apply)(${weakType.typeSymbol.companion}.unapply)"""

    c1.Expr[Mapping[A]](tree)
  }
}