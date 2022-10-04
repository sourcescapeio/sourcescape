package models.query.grammar

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class AutocompleteType(val identifier: String, val payload: PayloadType, val parsers: List[ParserType], val additional: JsObject = Json.obj()) extends Identifiable

object AutocompleteType {
  def render(all: Seq[AutocompleteType]) = {
    val trie = all.flatMap { item =>
      val flattened = Range(1, item.identifier.length).map { i =>
        item.identifier.substring(0, i)
      }

      flattened.map { k =>
        k -> item.identifier
      }
    }.groupBy(_._1).map {
      case (k, v) => k -> v.map(_._2)
    }

    val allItems = all.map { item =>
      item.identifier -> Json.obj(
        "payload" -> item.payload.toJson(item.additional),
        "parsers" -> item.parsers.map(_.identifier))
    }.toMap

    Json.obj(
      "items" -> Json.toJson(allItems),
      "trie" -> trie)
  }
}

object JavascriptAutocompleteType extends Plenumeration[AutocompleteType] {

  case object If extends AutocompleteType(
    "if",
    JavascriptPayloadType.IfStatement,
    List(JavascriptParserType.Default, JavascriptParserType.FunctionBody))

  case object Function extends AutocompleteType(
    "function",
    JavascriptPayloadType.FunctionDefinition,
    List(JavascriptParserType.Default, JavascriptParserType.FunctionBody))

  case object Class extends AutocompleteType(
    "class",
    JavascriptPayloadType.ClassDefinition,
    List(JavascriptParserType.Default))

  // should we have a new?
  // should we do unary prefixes?
  case object Throw extends AutocompleteType(
    "throw",
    JavascriptPayloadType.ThrowStatement,
    List(JavascriptParserType.Default, JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "predicate" -> "throw"))

  case object Return extends AutocompleteType(
    "return",
    JavascriptPayloadType.ReturnStatement,
    List(JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "predicate" -> "return"))

  case object Await extends AutocompleteType(
    "await",
    JavascriptPayloadType.AwaitStatement,
    List(JavascriptParserType.Default, JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "predicate" -> "await"))

  case object Yield extends AutocompleteType(
    "yield",
    JavascriptPayloadType.YieldStatement,
    List(JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "predicate" -> "yield"))

  case object This extends AutocompleteType(
    "this",
    JavascriptPayloadType.This,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody))
  case object Super extends AutocompleteType(
    "super",
    JavascriptPayloadType.Super,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody))

  case object Require extends AutocompleteType(
    "require",
    JavascriptPayloadType.Require,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody))

  // keywords
  case object True extends AutocompleteType(
    "true",
    JavascriptPayloadType.Keyword,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "name" -> "true"))

  case object False extends AutocompleteType(
    "false",
    JavascriptPayloadType.Keyword,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "name" -> "false"))

  case object Undefined extends AutocompleteType(
    "undefined",
    JavascriptPayloadType.Keyword,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "name" -> "undefined"))

  case object Null extends AutocompleteType(
    "null",
    JavascriptPayloadType.Keyword,
    List(
      JavascriptParserType.Default,
      JavascriptParserType.RootExpression,
      JavascriptParserType.BasicExpression,
      JavascriptParserType.FunctionBody),
    additional = Json.obj(
      "name" -> "null"))

}

object RubyAutocompleteType extends Plenumeration[AutocompleteType] {
  // case object Const extends AutocompleteType(
  //   "const",
  //   RubyPayloadType.Const,
  //   List(
  //     RubyParserType.Default))
}
