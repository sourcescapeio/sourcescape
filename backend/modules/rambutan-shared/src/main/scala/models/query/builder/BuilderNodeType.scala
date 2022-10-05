package models.query

import models.query.display._
import silvousplay.imports._

sealed abstract class BuilderNodeType(val identifier: String) extends Identifiable {

  def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]): DisplayStruct = {
    throw new Exception("not implemented")
  }
}

object JavascriptBuilderNodeType extends Plenumeration[BuilderNodeType] {

  //traverses
  case object Call extends BuilderNodeType("call") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.CallDisplay(id, None, Nil, alias)
    }
  }
  case object Member extends BuilderNodeType("member") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.MemberDisplay(id, None, name, alias)
    }
  }

  case object Object extends BuilderNodeType("object") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ObjectDisplay(id, Nil)
    }
  }

  case object Array extends BuilderNodeType("array") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ArrayDisplay(id, Nil)
    }
  }

  case object ObjectProperty extends BuilderNodeType("object-property") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ObjectPropertyDisplay(id, name, None)
    }
  }

  case object BinaryExpression extends BuilderNodeType("binary-expression") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.BinaryDisplay(id, name, None, None)
    }
  }
  case object UnaryExpression extends BuilderNodeType("unary-expression") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.UnaryDisplay(id, name, None)
    }
  }

  case object Class extends BuilderNodeType("class") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ClassDisplay(id, name, Nil, Nil, None)
    }
  }

  case object Instance extends BuilderNodeType("instance") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.InstanceDisplay(id, None, Nil)
    }
  }

  case object ClassMethod extends BuilderNodeType("class-method") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ClassMethodDisplay(id, name, Nil, Nil)
    }
  }

  case object ClassProperty extends BuilderNodeType("class-property") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ClassPropertyDisplay(id, name, None)
    }
  }

  case object Require extends BuilderNodeType("require") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.RequireDisplay(id, name, alias)
    }
  }

  case object Function extends BuilderNodeType("function") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.FunctionDisplay(id, name, Nil, Nil)
    }
  }

  case object FunctionArg extends BuilderNodeType("function-arg") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      FunctionArgDisplay(id, None, alias.getOrElse("arg"), index)
    }
  }

  case object Return extends BuilderNodeType("return") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.FunctionReturnDisplay(id, JavascriptHighlightType.FunctionReturn, "return", None)
    }
  }
  case object Await extends BuilderNodeType("await") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.FunctionReturnDisplay(id, JavascriptHighlightType.FunctionAwait, "await", None)
    }
  }
  case object Yield extends BuilderNodeType("yield") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.FunctionReturnDisplay(id, JavascriptHighlightType.FunctionYield, "yield", None)
    }
  }

  case object Throw extends BuilderNodeType("throw") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.FunctionReturnDisplay(id, JavascriptHighlightType.FunctionThrow, "throw", None)
    }
  }

  // terminal
  case object If extends BuilderNodeType("if") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.IfDisplay(id, None, Nil)
    }
  }

  case object Literal extends BuilderNodeType("literal") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.LiteralDisplay(id, name)
    }
  }
  case object LiteralString extends BuilderNodeType("literal-string") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.LiteralStringDisplay(id, name)
    }
  }

  case object This extends BuilderNodeType("this") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ThisDisplay(id)
    }
  }

  case object Super extends BuilderNodeType("super") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.SuperDisplay(id)
    }
  }

  case object Identifier extends BuilderNodeType("identifier") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.IdentifierDisplay(id, name)
    }
  }

  case object JSXElement extends BuilderNodeType("jsx-element") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.JSXElementDisplay(id, name, None, Nil, Nil)
    }
  }
  case object JSXAttribute extends BuilderNodeType("jsx-attribute") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.JSXAttributeDisplay(id, name, None)
    }
  }

  // templates
  case object TemplateLiteral extends BuilderNodeType("template-literal") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.TemplateLiteralDisplay(id, Nil)
    }
  }

  case object TemplateExpression extends BuilderNodeType("template-expression") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      // not implemented
      javascript.TemplateExpressionDisplay(id, index, None)
    }
  }
}

object ScalaBuilderNodeType extends Plenumeration[BuilderNodeType] {
  case object Trait extends BuilderNodeType("trait") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      javascript.ThisDisplay(id)
      // ReferenceDisplay(id, alias, Some(parentId), replace = true)
    }
  }
}

object RubyBuilderNodeType extends Plenumeration[BuilderNodeType] {
  case object Const extends BuilderNodeType("const") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.ConstDisplay(id, base = None, name = name)
    }
  }

  case object CBase extends BuilderNodeType("cbase") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.ConstDisplay(id, base = None, name = Some(""))
    }
  }

  case object CNull extends BuilderNodeType("cnull") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.CNullDisplay(id)
    }
  }

  case object Send extends BuilderNodeType("send") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.SendDisplay(id, base = None, name = name, args = Nil)
    }
  }

  case object SNull extends BuilderNodeType("snull") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.SNullDisplay(id)
    }
  }

  // data
  case object Array extends BuilderNodeType("array") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.ArrayDisplay(id, Nil)
    }
  }

  case object Hash extends BuilderNodeType("hash") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.HashDisplay(id, Nil)
    }
  }

  case object HashPair extends BuilderNodeType("hash-pair") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.HashPairDisplay(id, name, None)
    }
  }

  // lit
  case object Literal extends BuilderNodeType("literal") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.LiteralDisplay(id, name)
    }
  }
  case object LiteralString extends BuilderNodeType("literal-string") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ruby.LiteralStringDisplay(id, name)
    }
  }
}

object BuilderNodeType {
  case object Dep extends BuilderNodeType("dep") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      DepDisplay(id, parentId, None)
    }
  }

  // Do we want an Any?
  case object Any extends BuilderNodeType("any") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      // ReferenceDisplay(id, alias, Some(parentId), replace = false)
      AnyDisplay(id, parentId, false)
    }
  }

  case object Reference extends BuilderNodeType("reference") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      ReferenceDisplay(id, alias, Some(parentId), replace = true)
    }
  }

  case object Search extends BuilderNodeType("search") {
    override def displayStruct(id: String, parentId: String, name: Option[String], index: Option[Int], alias: Option[String]) = {
      AnySearchDisplay(id, name)
    }
  }

  private def fromJavascriptPredicate(predicate: JavascriptNodePredicate) = {
    predicate match {
      case JavascriptNodePredicate.Array              => JavascriptBuilderNodeType.Array
      case JavascriptNodePredicate.Object             => JavascriptBuilderNodeType.Object
      case JavascriptNodePredicate.ObjectProperty     => JavascriptBuilderNodeType.ObjectProperty
      case JavascriptNodePredicate.BinaryExpression   => JavascriptBuilderNodeType.BinaryExpression
      case JavascriptNodePredicate.UnaryExpression    => JavascriptBuilderNodeType.UnaryExpression
      //
      case JavascriptNodePredicate.Class              => JavascriptBuilderNodeType.Class
      case JavascriptNodePredicate.ClassMethod        => JavascriptBuilderNodeType.ClassMethod
      case JavascriptNodePredicate.ClassProperty      => JavascriptBuilderNodeType.ClassProperty
      case JavascriptNodePredicate.Instance           => JavascriptBuilderNodeType.Instance
      //
      case JavascriptNodePredicate.Require            => JavascriptBuilderNodeType.Require
      // func
      case JavascriptNodePredicate.Function           => JavascriptBuilderNodeType.Function
      case JavascriptNodePredicate.FunctionArg        => JavascriptBuilderNodeType.FunctionArg
      case JavascriptNodePredicate.Return             => JavascriptBuilderNodeType.Return
      case JavascriptNodePredicate.Yield              => JavascriptBuilderNodeType.Yield
      case JavascriptNodePredicate.Await              => JavascriptBuilderNodeType.Await
      // control
      case JavascriptNodePredicate.If                 => JavascriptBuilderNodeType.If
      // error
      case JavascriptNodePredicate.Throw              => JavascriptBuilderNodeType.Throw
      // base
      case JavascriptNodePredicate.Literal            => JavascriptBuilderNodeType.Literal
      case JavascriptNodePredicate.LiteralString      => JavascriptBuilderNodeType.LiteralString
      case JavascriptNodePredicate.This               => JavascriptBuilderNodeType.This
      case JavascriptNodePredicate.Super              => JavascriptBuilderNodeType.Super
      case JavascriptNodePredicate.Identifier         => JavascriptBuilderNodeType.Identifier
      case JavascriptNodePredicate.JSXElement         => JavascriptBuilderNodeType.JSXElement
      case JavascriptNodePredicate.JSXAttribute       => JavascriptBuilderNodeType.JSXAttribute
      // templates
      case JavascriptNodePredicate.TemplateLiteral    => JavascriptBuilderNodeType.TemplateLiteral
      case JavascriptNodePredicate.TemplateExpression => JavascriptBuilderNodeType.TemplateExpression
      // general mappings
      case JavascriptNodePredicate.Call               => JavascriptBuilderNodeType.Call
      case JavascriptNodePredicate.Member             => JavascriptBuilderNodeType.Member
      // invalid
      case JavascriptNodePredicate.NotIdentifierRef   => throw new Exception("invalid predicate")
    }
  }

  private def fromRubyPredicate(s: RubyNodePredicate): BuilderNodeType = {
    s match {
      case RubyNodePredicate.Const          => RubyBuilderNodeType.Const
      case RubyNodePredicate.CBase          => RubyBuilderNodeType.CBase
      case RubyNodePredicate.CNull          => RubyBuilderNodeType.CNull
      case RubyNodePredicate.Send           => RubyBuilderNodeType.Send
      case RubyNodePredicate.SNull          => RubyBuilderNodeType.SNull
      // data
      case RubyNodePredicate.Array          => RubyBuilderNodeType.Array
      case RubyNodePredicate.Hash           => RubyBuilderNodeType.Hash
      case RubyNodePredicate.HashPair       => RubyBuilderNodeType.HashPair
      // literals
      case RubyNodePredicate.KeywordLiteral => RubyBuilderNodeType.Literal
      case RubyNodePredicate.NumberLiteral  => RubyBuilderNodeType.Literal
      case RubyNodePredicate.StringLiteral  => RubyBuilderNodeType.LiteralString
    }
  }

  def fromPredicate(predicate: NodePredicate) = {
    predicate match {
      case j: JavascriptNodePredicate    => fromJavascriptPredicate(j)
      case r: RubyNodePredicate          => fromRubyPredicate(r)
      case UniversalNodePredicate.Dep    => BuilderNodeType.Dep
      case UniversalNodePredicate.Search => BuilderNodeType.Search
      case g: GenericGraphNodePredicate  => throw new Exception("not implemented")
    }
  }

  // should never be accessed?
  // lazy val all = List(Dep, Any, Reference, Search) ++ JavascriptBuilderNodeType.all ++ ScalaBuilderNodeType.all
}
