package models.query

import models.IndexType
import models.index.NodeType
import models.index.esprima.ESPrimaNodeType
import models.index.scalameta.ScalaMetaNodeType
import models.index.ruby.RubyNodeType
import models.graph._
import silvousplay.imports._
import models.IndexType.Javascript

sealed abstract class NodePredicate(val identifier: String) extends Identifiable {
  val forceRoot: Boolean = false

  def filters(conditions: Option[Condition]): List[NodeFilter]
}

sealed abstract class SimpleNodePredicate(identifierIn: String, val nodeType: NodeType, category: String) extends NodePredicate(s"${category}::${identifierIn}") {
  // val negate: Boolean = false

  def filters(conditions: Option[Condition]) = {
    typeFilter :: conditions.map(_.filter).toList
  }

  protected def typeFilter: NodeFilter = {
    NodeTypeFilter(nodeType)
  }
}

/**
 * Ruby
 */
sealed abstract class RubyNodePredicate(identifierIn: String, nodeTypeIn: RubyNodeType)
  extends SimpleNodePredicate(identifierIn, nodeTypeIn, "ruby")

object RubyNodePredicate extends Plenumeration[RubyNodePredicate] {
  case object CBase extends RubyNodePredicate("cbase", RubyNodeType.CBase)
  case object Const extends RubyNodePredicate("const", RubyNodeType.Const)
  case object CNull extends RubyNodePredicate("cnull", RubyNodeType.CNull)

  case object Send extends RubyNodePredicate("send", RubyNodeType.Send)
  case object SNull extends RubyNodePredicate("snull", RubyNodeType.SNull)

  case object KeywordLiteral extends RubyNodePredicate("keyword", RubyNodeType.KeywordLiteral)
  case object StringLiteral extends RubyNodePredicate("string", RubyNodeType.Str)
  case object NumberLiteral extends RubyNodePredicate("number", RubyNodeType.NumberLiteral)

  case object Array extends RubyNodePredicate("array", RubyNodeType.Array)
  case object Hash extends RubyNodePredicate("hash", RubyNodeType.Hash)
  case object HashPair extends RubyNodePredicate("hash_pair", RubyNodeType.Pair)
}

/**
 * Scala
 */
sealed abstract class ScalaNodePredicate(identifierIn: String, nodeTypeIn: ScalaMetaNodeType)
  extends SimpleNodePredicate(identifierIn, nodeTypeIn, "scala")

object ScalaNodePredicate extends Plenumeration[ScalaNodePredicate] {
  case object Trait extends ScalaNodePredicate("trait", ScalaMetaNodeType.Trait) {
  }
}

/**
 * Javascript
 */
sealed abstract class JavascriptNodePredicate(identifierIn: String, nodeTypeIn: ESPrimaNodeType)
  extends SimpleNodePredicate(identifierIn, nodeTypeIn, "javascript")

object JavascriptNodePredicate extends Plenumeration[JavascriptNodePredicate] {
  case object Class extends JavascriptNodePredicate("class", ESPrimaNodeType.Class) {
    override val forceRoot: Boolean = true
  }
  case object ClassProperty extends JavascriptNodePredicate("class-property", ESPrimaNodeType.ClassProperty)
  case object ClassMethod extends JavascriptNodePredicate("class-method", ESPrimaNodeType.Method)
  case object Instance extends JavascriptNodePredicate("instance", ESPrimaNodeType.Instance)

  case object Require extends JavascriptNodePredicate("require", ESPrimaNodeType.Require) {
    override val forceRoot = true
  }

  case object Function extends JavascriptNodePredicate("function", ESPrimaNodeType.Function)
  case object FunctionArg extends JavascriptNodePredicate("function-arg", ESPrimaNodeType.FunctionArg)
  case object Return extends JavascriptNodePredicate("return", ESPrimaNodeType.Return)
  case object Yield extends JavascriptNodePredicate("yield", ESPrimaNodeType.Yield)
  case object Await extends JavascriptNodePredicate("await", ESPrimaNodeType.Await)

  case object Throw extends JavascriptNodePredicate("throw", ESPrimaNodeType.Throw)

  case object BinaryExpression extends JavascriptNodePredicate("binary-expression", ESPrimaNodeType.BinaryExp)
  case object UnaryExpression extends JavascriptNodePredicate("unary-expression", ESPrimaNodeType.UnaryExp)

  case object If extends JavascriptNodePredicate("if", ESPrimaNodeType.If)

  case object Literal extends JavascriptNodePredicate("literal", ESPrimaNodeType.Literal)
  case object LiteralString extends JavascriptNodePredicate("literal-string", ESPrimaNodeType.Literal) {
    // override name filter
    override def filters(conditions: Option[Condition]) = {
      val maybeName = conditions match {
        case Some(NameCondition(n)) => NodeNameFilter("\"" + n + "\"") :: Nil
        case _                      => Nil
      }

      typeFilter :: maybeName
    }
  }
  case object This extends JavascriptNodePredicate("this", ESPrimaNodeType.This)
  case object Super extends JavascriptNodePredicate("super", ESPrimaNodeType.Super)

  case object JSXElement extends JavascriptNodePredicate("jsx-element", ESPrimaNodeType.JSXElement)

  case object Identifier extends JavascriptNodePredicate("identifier", ESPrimaNodeType.IdentifierRef)

  case object Array extends JavascriptNodePredicate("array", ESPrimaNodeType.Array)
  case object Object extends JavascriptNodePredicate("object", ESPrimaNodeType.Object)

  // generated by edge predicate. can use directly, but superfluous
  case object ObjectProperty extends JavascriptNodePredicate("object-property", ESPrimaNodeType.ObjectProp)
  case object JSXAttribute extends JavascriptNodePredicate("jsx-attribute", ESPrimaNodeType.JSXAttribute)
  case object Call extends JavascriptNodePredicate("call", ESPrimaNodeType.Call)
  case object Member extends JavascriptNodePredicate("member", ESPrimaNodeType.Member)

  // templates
  case object TemplateLiteral extends JavascriptNodePredicate("template-literal", ESPrimaNodeType.TemplateLiteral)
  case object TemplateExpression extends JavascriptNodePredicate("template-expression", ESPrimaNodeType.TemplateExpression)

  // fallback for references
  case object NotIdentifierRef extends JavascriptNodePredicate("not-identifier-ref", ESPrimaNodeType.IdentifierRef) {
    override protected def typeFilter = NodeNotTypesFilter(nodeType :: Nil)
  }
}

sealed abstract class GenericGraphNodePredicate(nodeTypeIn: GenericGraphNodeType)
  extends SimpleNodePredicate(nodeTypeIn.identifier, nodeTypeIn, "generic")

object GenericGraphNodePredicate extends Plenumeration[GenericGraphNodePredicate] {
  case object Table extends GenericGraphNodePredicate(GenericGraphNodeType.Table)
  case object Row extends GenericGraphNodePredicate(GenericGraphNodeType.Row)
  case object Cell extends GenericGraphNodePredicate(GenericGraphNodeType.Cell)

  // snapshots
  case object Schema extends GenericGraphNodePredicate(GenericGraphNodeType.Schema)
  case object SchemaColumn extends GenericGraphNodePredicate(GenericGraphNodeType.SchemaColumn)

  case object Snapshot extends GenericGraphNodePredicate(GenericGraphNodeType.Snapshot)
  case object SnapshotRow extends GenericGraphNodePredicate(GenericGraphNodeType.SnapshotRow)
  case object SnapshotCell extends GenericGraphNodePredicate(GenericGraphNodeType.SnapshotCell)
  case object SnapshotCellData extends GenericGraphNodePredicate(GenericGraphNodeType.SnapshotCellData)

  case object Annotation extends GenericGraphNodePredicate(GenericGraphNodeType.Annotation)

  // git
  case object GitCommit extends GenericGraphNodePredicate(GenericGraphNodeType.GitCommit)
  case object GitHead extends GenericGraphNodePredicate(GenericGraphNodeType.GitHead)
  case object CodeIndex extends GenericGraphNodePredicate(GenericGraphNodeType.CodeIndex)
}

object UniversalNodePredicate extends Plenumeration[NodePredicate] {
  // should this be universal across languages?
  case object Dep extends NodePredicate("dep") {
    override def filters(condition: Option[Condition]) = {
      Nil
    }
  }

  case object Search extends NodePredicate("search") {
    override def filters(condition: Option[Condition]) = {
      condition.map(_.filter).toList
    }
  }
}

// We only use reads here
object NodePredicate extends Plenumeration[NodePredicate] {
  override val all = {
    UniversalNodePredicate.all ++ GenericGraphNodePredicate.all ++ IndexType.all.flatMap(_.nodePredicate.all)
  }
}