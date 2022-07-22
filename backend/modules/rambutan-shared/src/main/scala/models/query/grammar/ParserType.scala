package models.query.grammar

import silvousplay.imports._
import play.api.libs.json._

// TO grammar
sealed abstract class ParserType(val identifier: String) extends Identifiable {

  val grammars: List[Grammar]

  val incompletes: List[Grammar]

  def start = GrammarBuilder("start").or(grammars: _*)

  def incompleteStart = GrammarBuilder("start").or(incompletes: _*)

  private def flattenGrammars(grammars: List[Grammar], visited: List[Grammar]): List[Grammar] = {
    val visitedSet = visited.map(_.getId).toSet
    val filteredGrammars = grammars.filterNot(g => visitedSet.contains(g.getId))
    val recursed = filteredGrammars.flatMap { g =>
      g :: g.getPeg.segments flatMap {
        case MultiGrammar(inner) => flattenGrammars(inner, filteredGrammars ++ visited)
        case _                   => Nil
      }
    }

    (grammars ++ recursed).distinct
  }

  def flattenedPayloads = {
    val baseGrammar = flattenGrammars(grammars, Nil)

    baseGrammar.flatMap { b =>
      b.getReturnStrategy match {
        case s: PayloadReturnStrategy => Some(s.payloadType)
        case _                        => None
      }
    }.distinct
  }
}

sealed abstract class DefaultParserType extends ParserType("default")
sealed abstract class TraverseParserType extends ParserType("traverse")

sealed trait JavascriptParserType {
  self: ParserType =>
}

object JavascriptParserType extends Plenumeration[ParserType] {
  case object ClassBody extends ParserType("classBody") with JavascriptParserType {
    val grammars = List(
      javascript.Classes.MethodDefinition,
      javascript.Classes.ClassProperty)

    val incompletes = List(
      javascript.ClassesIncomplete.MethodIncomplete,
      javascript.ClassesIncomplete.ClassPropertyIncomplete)
  }

  case object FunctionBody extends ParserType("functionBody") with JavascriptParserType {
    val grammars = List(
      javascript.Expressions.Expression,
      javascript.Statements.InternalStatement,
      javascript.Functions.FunctionInternalStatement)

    val incompletes = List(
      javascript.ExpressionsIncomplete.ExpressionIncomplete,
      javascript.StatementsIncomplete.InternalStatementIncomplete)
  }

  case object JSXAttribute extends ParserType("jsxAttribute") with JavascriptParserType {
    val grammars = javascript.JSX.JSXAttribute :: Nil

    val incompletes = javascript.JSXIncomplete.JSXAttributeIncomplete :: Nil
  }

  case object ObjectProperty extends ParserType("objectProperty") with JavascriptParserType {
    val grammars = javascript.Data.ObjectAttribute :: Nil

    val incompletes = List(
      javascript.DataIncomplete.ObjectAttributeIncomplete)
  }

  case object RootExpression extends ParserType("rootExpression") with JavascriptParserType {
    val grammars = javascript.Traverses.RootExpression :: Nil

    val incompletes = {
      javascript.TraversesIncomplete.RootedIncomplete :: Nil
    }
  }

  case object BasicExpression extends ParserType("basicExpression") with JavascriptParserType {
    val grammars = javascript.Expressions.BasicExpression :: Nil

    val incompletes = {
      javascript.ExpressionsIncomplete.BasicExpressionIncomplete :: Nil
    }
  }

  // TODO: sourcescape that every plenum includes a traverse and default?
  case object Traverse extends TraverseParserType with JavascriptParserType {
    val grammars = List(
      javascript.Traverses.TraverseList)

    val incompletes = List(
      javascript.TraversesIncomplete.TraverseListIncomplete)
  }

  case object Default extends DefaultParserType with JavascriptParserType {
    val grammars = List(
      javascript.Expressions.Expression,
      javascript.Statements.Statement)

    val incompletes = List(
      javascript.ExpressionsIncomplete.ExpressionIncomplete,
      javascript.StatementsIncomplete.StatementIncomplete)
  }
}

object ScalaParserType extends Plenumeration[ParserType] {
  case object Default extends ParserType("default") {
    val grammars = List(
      scalap.Expressions.Trait)

    val incompletes = List(
      scalap.Expressions.Trait)
  }
}

sealed trait RubyParserType {
  self: ParserType =>
}

object RubyParserType extends Plenumeration[ParserType] {
  case object HashElement extends ParserType("hashElement") with RubyParserType {
    val grammars = List(
      ruby.Data.HashPair)

    val incompletes = List(
      ruby.DataIncomplete.HashPairIncomplete)
  }

  case object Traverse extends TraverseParserType with RubyParserType {
    val grammars = List(
      ruby.Traverses.TraverseList)

    val incompletes = List(
      ruby.TraversesIncomplete.IncompleteSendTraverseList) // traverse incomplete
  }

  case object ConstTraverse extends ParserType("constTraverse") with RubyParserType {
    val grammars = List(
      ruby.Traverses.ConstPrefixTraverseList,
      ruby.Traverses.TraverseList
    // ruby.Traverses.SendPrefixTraverseList
    )

    val incompletes = List(
      ruby.TraversesIncomplete.IncompleteSendTraverseListWithConstPrefix,
      ruby.TraversesIncomplete.IncompleteSendTraverseList)
  }

  case object TraverseWithArg extends ParserType("traverseWithArg") with RubyParserType {
    val grammars = List(
      ruby.Traverses.AppendArg,
      ruby.Traverses.TraverseList)

    val incompletes = List(
      ruby.TraversesIncomplete.IncompleteSendTraverseList) // traverse incomplete
  }

  case object Default extends DefaultParserType with RubyParserType {
    val grammars = List(
      ruby.Expressions.Expression)

    val incompletes = List(
      ruby.ExpressionsIncomplete.ExpressionIncomplete)
  }
}

// TODO: fugly
object ParserType {
  // these are not used, but used as
  case object Default extends DefaultParserType {
    val grammars = Nil
    val incompletes = Nil
  }

  case object Traverse extends TraverseParserType {
    val grammars = Nil
    val incompletes = Nil
  }

  implicit val writes = new Writes[ParserType] {
    def writes(obj: ParserType): JsValue = JsString(obj.identifier)
  }
}
