package models.extractor.scalameta

import silvousplay.imports._
import models.index.scalameta._
import scala.meta._

object defn {
  def extractTemplate(templ: Template)(context: ScalaMetaContext): Unit = {
    // https://www.javadoc.io/doc/org.scalameta/trees_2.12/latest/scala/meta/Template.html
    println("DERIVES", templ.derives)
    println("INITS", templ.inits)
    // templ.stats.foreach(Source.extractTree _)
  }

  object Object {
    def extract(o: Defn.Object)(context: ScalaMetaContext): ExpressionWrapper[ScalaMetaNodeBuilder] = {
      // https://www.javadoc.io/doc/org.scalameta/trees_2.12/latest/scala/meta/Defn$$Object.html
      // o.mods << [private, sealed, etc.]
      println("object")
      println(o.name.value)
      extractTemplate(o.templ)(context)
      val node = ObjectNode(Hashing.uuid, codeRange(o.pos), o.name.value)
      ExpressionWrapper(
        node,
        codeRange(o.pos),
        Nil,
        Nil,
        Nil)
    }
  }

  object Trait {
    def extract(t: Defn.Trait)(context: ScalaMetaContext): ExpressionWrapper[TraitNode] = {
      // https://www.javadoc.io/doc/org.scalameta/trees_2.12/latest/scala/meta/Defn$$Trait.html
      println("trait")
      println(t.name.value)
      println(t.tparams) // not sure what this is

      extractTemplate(t.templ)(context)

      val node = TraitNode(Hashing.uuid, codeRange(t.pos), t.name.value)
      ExpressionWrapper(
        node,
        codeRange(t.pos),
        Nil,
        Nil,
        Nil)
    }
  }

  // object Def {
  //   def extract(t: Defn.Def) = {

  //   }
  // }
}