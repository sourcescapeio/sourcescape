package silvousplay.macros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import play.api.libs.json._

class overrideType(from: String, to: String)

case class Descriptor[T](items: List[DescriptorItem])

case class DescriptorItem(
  name: String, typeName: String, args: List[String])

object DescriptorItem {
  implicit val writes = Json.writes[DescriptorItem]
}

trait DescriptorHelpers {
  type overrideType = silvousplay.macros.overrideType

  val Descriptors = silvousplay.macros.DescriptorHelper

  type Descriptor[T] = silvousplay.macros.Descriptor[T]
}

object DescriptorHelper {
  def buildDescriptors[T]: Descriptor[T] = macro DescriptorHelperImpl.buildDescriptors_impl[T]
}

object DescriptorHelperImpl {
  def buildDescriptors_impl[A](c1: blackbox.Context)(implicit evidence1: c1.WeakTypeTag[A]): c1.Expr[Descriptor[A]] = {
    import c1.universe._
    import c1.universe.Flag._

    def extractTypes(a: c1.Type): (String, Tree) = {
      if (a <:< weakTypeOf[Option[_]]) {
        a match {
          case TypeRef(_, _, targ :: Nil) => extractTypes(targ)
          case _                          => c1.abort(c1.enclosingPosition, "call this method with known type parameter only.")
        }
      } else if (a.typeSymbol.companion.typeSignature <:< weakTypeOf[MacroEnum[_, _]]) {
        (a.toString, q"""${a.typeSymbol.companion}.extractIdentifier().toList.map(_.toString)""")
      } else {
        (a.toString, q"Nil")
      }
    }

    val weakType = weakTypeOf[A]
    val overrides = weakType.typeSymbol.annotations.collect {
      case ann if ann.tree.tpe <:< c1.weakTypeOf[overrideType] => ann.tree match {
        case Apply(c, List(Literal(Constant(a: String)), Literal(Constant(b: String)))) => {
          Map(a -> b)
        }
      }
    }.foldLeft(Map.empty[String, String])(_ ++ _)
    val decls = weakType.decls.flatMap {
      case x: MethodSymbol if x.isPublic && x.isGetter => {
        val stringName = s"""${x.name}"""
        val returnType = x.returnType
        val (typeName, args) = extractTypes(returnType)
        val defaultedType = overrides.getOrElse(stringName, typeName)
        Some(q"""
          silvousplay.macros.DescriptorItem(
            $stringName, $defaultedType, $args
          )
        """)
      }
      case _ => None
    }

    c1.Expr[Descriptor[A]](q"""
      silvousplay.macros.Descriptor[$weakType](
        List(
          ..$decls
        )
      )
    """)
  }
}
