package models.query

import models.index.esprima.ESPrimaEdgeType
import models.index.ruby.RubyEdgeType
import models.index.{ EdgeType, NodeType }
import models.IndexType
import models.graph._
import silvousplay.imports._
import play.api.libs.json._

sealed class GraphEdgeType(
  val identifier: String,
  val edgeType:   EdgeType,
  val direction:  AccessDirection,
  val crossFile:  Boolean) extends Identifiable {

  override def toString() = s"${identifier}"

  def isContainsForward = {
    edgeType.isContains && direction =?= AccessDirection.From
  }

  private def reverseIdentifier = {
    if (identifier.endsWith(".reverse")) {
      identifier.replaceFirst("\\.reverse$", "")
    } else {
      s"${identifier}.reverse"
    }
  }

  def opposite: GraphEdgeType = new GraphEdgeType(
    reverseIdentifier,
    edgeType,
    direction.reverse,
    crossFile)
}

sealed class RubyGraphEdgeType(edgeTypeIn: RubyEdgeType, direction: AccessDirection)
  extends GraphEdgeType(s"${IndexType.Ruby.identifier}::${edgeTypeIn.identifier}", edgeTypeIn, direction, false)

object RubyGraphEdgeType extends Plenumeration[RubyGraphEdgeType] {
  val follows = List(
    Assignment,
    Reference)

  case object Const extends RubyGraphEdgeType(RubyEdgeType.Const, AccessDirection.To)
  case object Send extends RubyGraphEdgeType(RubyEdgeType.SendObject, AccessDirection.To)

  case object SendArg extends RubyGraphEdgeType(RubyEdgeType.SendArg, AccessDirection.From)

  case object ArrayElement extends RubyGraphEdgeType(RubyEdgeType.ArrayElement, AccessDirection.From)
  case object HashElement extends RubyGraphEdgeType(RubyEdgeType.HashElement, AccessDirection.From)
  case object PairValue extends RubyGraphEdgeType(RubyEdgeType.PairValue, AccessDirection.From)

  // follows
  case object Assignment extends RubyGraphEdgeType(RubyEdgeType.Assignment, AccessDirection.To)
  case object Reference extends RubyGraphEdgeType(RubyEdgeType.Reference, AccessDirection.To)
}

sealed class JavascriptGraphEdgeType(identifierIn: String, edgeTypeIn: ESPrimaEdgeType, direction: AccessDirection, crossFile: Boolean)
  extends GraphEdgeType(s"${IndexType.Javascript.identifier}::${identifierIn}", edgeTypeIn, direction, crossFile)

object JavascriptGraphEdgeType extends Plenumeration[JavascriptGraphEdgeType] {

  val follows = List(
    DeclaredAs,
    AssignedAs,
    ReferenceOf)

  case object DeclaredAs extends JavascriptGraphEdgeType("declared_as", ESPrimaEdgeType.Declare, AccessDirection.To, crossFile = false)
  case object AssignedAs extends JavascriptGraphEdgeType("assigned_as", ESPrimaEdgeType.Assignment, AccessDirection.To, crossFile = false)

  case object ReferenceOf extends JavascriptGraphEdgeType("reference_of", ESPrimaEdgeType.Reference, AccessDirection.To, crossFile = false)
  case object InstanceOf extends JavascriptGraphEdgeType("instance_of", ESPrimaEdgeType.Class, AccessDirection.From, crossFile = false)

  case object ArgOf extends JavascriptGraphEdgeType("arg_of", ESPrimaEdgeType.Argument, AccessDirection.To, crossFile = false)

  case object CallOf extends JavascriptGraphEdgeType("call_of", ESPrimaEdgeType.Call, AccessDirection.To, crossFile = false)
  case object MemberOf extends JavascriptGraphEdgeType("member_of", ESPrimaEdgeType.Member, AccessDirection.To, crossFile = false)

  case object ArrayMember extends JavascriptGraphEdgeType("array_member", ESPrimaEdgeType.ArrayMember, AccessDirection.From, crossFile = false)
  case object ValueInObject extends JavascriptGraphEdgeType("value_in_object", ESPrimaEdgeType.ObjectValue, AccessDirection.From, crossFile = false)

  case object ClassConstructor extends JavascriptGraphEdgeType("constructor", ESPrimaEdgeType.Constructor, AccessDirection.From, crossFile = false)
  case object ClassExtends extends JavascriptGraphEdgeType("class_extends", ESPrimaEdgeType.SuperClass, AccessDirection.From, crossFile = false)
  case object ClassDecorator extends JavascriptGraphEdgeType("class_decorator", ESPrimaEdgeType.ClassDecorator, AccessDirection.From, crossFile = false)
  case object ClassMethod extends JavascriptGraphEdgeType("class_method", ESPrimaEdgeType.Method, AccessDirection.From, crossFile = false)
  case object ClassProperty extends JavascriptGraphEdgeType("class_property", ESPrimaEdgeType.ClassProperty, AccessDirection.From, crossFile = false)
  case object ClassPropertyValue extends JavascriptGraphEdgeType("class_property_value", ESPrimaEdgeType.ClassPropertyValue, AccessDirection.From, crossFile = false)

  case object MethodFunction extends JavascriptGraphEdgeType("method_function", ESPrimaEdgeType.MethodFunction, AccessDirection.From, crossFile = false)
  case object MethodDecorator extends JavascriptGraphEdgeType("method_decorator", ESPrimaEdgeType.MethodDecorator, AccessDirection.From, crossFile = false)
  case object FunctionArgument extends JavascriptGraphEdgeType("function_argument", ESPrimaEdgeType.FunctionArgument, AccessDirection.From, crossFile = false)

  case object FunctionContains extends JavascriptGraphEdgeType("function_contains", ESPrimaEdgeType.FunctionContains, AccessDirection.From, crossFile = false)
  case object FunctionReturn extends JavascriptGraphEdgeType("function_return", ESPrimaEdgeType.FunctionReturn, AccessDirection.To, crossFile = false) //dep?
  case object ReturnContains extends JavascriptGraphEdgeType("return_contains", ESPrimaEdgeType.ReturnContains, AccessDirection.From, crossFile = false)
  case object Return extends JavascriptGraphEdgeType("return", ESPrimaEdgeType.Return, AccessDirection.From, crossFile = false)
  case object Yield extends JavascriptGraphEdgeType("yield", ESPrimaEdgeType.Yield, AccessDirection.From, crossFile = false)
  case object Await extends JavascriptGraphEdgeType("await", ESPrimaEdgeType.Await, AccessDirection.From, crossFile = false)
  case object Throw extends JavascriptGraphEdgeType("throw", ESPrimaEdgeType.Throw, AccessDirection.From, crossFile = false)

  case object JSXAttribute extends JavascriptGraphEdgeType("jsx_attribute", ESPrimaEdgeType.JSXAttribute, AccessDirection.From, crossFile = false)
  case object JSXAttributeValue extends JavascriptGraphEdgeType("jsx_attribute_value", ESPrimaEdgeType.JSXAttributeValue, AccessDirection.From, crossFile = false)
  case object JSXTag extends JavascriptGraphEdgeType("jsx_tag", ESPrimaEdgeType.JSXTag, AccessDirection.From, crossFile = false)
  case object JSXChild extends JavascriptGraphEdgeType("jsx_child", ESPrimaEdgeType.JSXChild, AccessDirection.From, crossFile = false)

  case object ObjectProperty extends JavascriptGraphEdgeType("object_property", ESPrimaEdgeType.ObjectProperty, AccessDirection.From, crossFile = false)
  case object ObjectValue extends JavascriptGraphEdgeType("object_value", ESPrimaEdgeType.ObjectValue, AccessDirection.From, crossFile = false)

  case object TemplateLiteral extends JavascriptGraphEdgeType("template_literal", ESPrimaEdgeType.TemplateLiteral, AccessDirection.From, crossFile = false)
  case object TemplateContains extends JavascriptGraphEdgeType("template_contains", ESPrimaEdgeType.TemplateContains, AccessDirection.From, crossFile = false)

  // control
  case object IfBlock extends JavascriptGraphEdgeType("if_block", ESPrimaEdgeType.IfBlock, AccessDirection.From, crossFile = false)
  case object IfTest extends JavascriptGraphEdgeType("if_test", ESPrimaEdgeType.IfTest, AccessDirection.From, crossFile = false)
  case object IfTestContains extends JavascriptGraphEdgeType("if_test_contains", ESPrimaEdgeType.IfTestContains, AccessDirection.From, crossFile = false)
  case object IfContains extends JavascriptGraphEdgeType("if_contains", ESPrimaEdgeType.IfContains, AccessDirection.From, crossFile = false)
  case object BasicExpression extends JavascriptGraphEdgeType("basic_expression", ESPrimaEdgeType.BasicExpression, AccessDirection.To, crossFile = false)

  // Export links (TODO: delete?)
  // case object ExportKeyLink extends JavascriptGraphEdgeType("export_key_link", ESPrimaEdgeType.ExportKey, AccessDirection.From)
  // case object ExportedTo extends JavascriptGraphEdgeType("exported_to", ESPrimaEdgeType.Export, AccessDirection.From)
  // case object LinkedTo extends JavascriptGraphEdgeType("linked_to", ESPrimaEdgeType.Link, AccessDirection.To)

  // Links
  case object CallLink extends JavascriptGraphEdgeType("call_link", ESPrimaEdgeType.CallLink, AccessDirection.From, crossFile = true)
}

sealed class GenericGraphEdgeType(category: String, edgeTypeIn: GenericEdgeType, direction: AccessDirection)
  extends GraphEdgeType(edgeTypeIn.identifier, edgeTypeIn, direction, false)

private object GenericEdgeCategories {
  val SnapshotCategory = "snapshot"
  val GitCategory = "git"
}

object GenericGraphEdgeType extends Plenumeration[GenericGraphEdgeType] {
  import GenericEdgeCategories._

  case object Test extends GenericGraphEdgeType("test", GenericEdgeType.Test, AccessDirection.To)

  case object TableRow extends GenericGraphEdgeType("table", GenericEdgeType.TableRow, AccessDirection.From)
  case object RowCell extends GenericGraphEdgeType("table", GenericEdgeType.RowCell, AccessDirection.From)

  case object SnapshotRow extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotRow, AccessDirection.From)
  case object SnapshotRowCell extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotRowCell, AccessDirection.From)
  case object SnapshotColumnCell extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotColumnCell, AccessDirection.From)
  case object SnapshotCellData extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotCellData, AccessDirection.From)

  case object SnapshotRowAnnotation extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotRowAnnotation, AccessDirection.From)

  case object SnapshotCodeIndex extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SnapshotCodeIndex, AccessDirection.From)

  case object SchemaColumn extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SchemaColumn, AccessDirection.From)
  case object SchemaSnapshot extends GenericGraphEdgeType(SnapshotCategory, GenericEdgeType.SchemaSnapshot, AccessDirection.From)

  case object GitCommitParent extends GenericGraphEdgeType(GitCategory, GenericEdgeType.GitCommitParent, AccessDirection.From)
  case object GitHeadCommit extends GenericGraphEdgeType(GitCategory, GenericEdgeType.GitHeadCommit, AccessDirection.From)
  case object GitCommitIndex extends GenericGraphEdgeType(GitCategory, GenericEdgeType.GitCommitIndex, AccessDirection.From)
}

// Not a plenumeration to avoid issues
// extends Plenumeration[GraphEdgeType]
object GraphEdgeType {

  val all = JavascriptGraphEdgeType.all ++ RubyGraphEdgeType.all

  val follows = JavascriptGraphEdgeType.follows ++ RubyGraphEdgeType.follows
  // This is only used for
}
