package models.query.grammar.javascript

import models.query.grammar._
import Base._
import models.query.JavascriptNodePredicate

object ClassesIncomplete extends GrammarHelpers {
  def MethodIncomplete = {
    grammar("MethodIncomplete")
      .payload(JavascriptPayloadType.MethodDefinition) {
        named("m" -> Ident.IdentifierExpression) ~ __ ~
          named("args" -> FunctionsIncomplete.FunctionIncompleteArguments)
      }
  }

  private def ClassPropertyValueIncomplete = {
    grammar("ClassPropertyValueIncomplete")
      .single("r") {
        named("r" -> ?(ExpressionsIncomplete.AnyBasicExpression))
      }
  }

  def ClassPropertyIncomplete = {
    grammar("ClassPropertyIncomplete")
      .payload(JavascriptPayloadType.ClassProperty) {
        named("p" -> Ident.IdentifierExpression) ~ __ ~
          k("=") ~ __ ~
          named("v" -> ClassPropertyValueIncomplete)
      }
  }

  private def ExtendsAll = multi(k("extends"), k("extend"), k("exten"), k("exte"), k("ext"), k("ex"), k("e"))

  private def ClassExtendsIncomplete = {
    grammar("ClassExtendsIncomplete")
      .payload(JavascriptPayloadType.ClassDefinition) {
        k("class") ~
          named("name" -> ?(Ident.DefinitionName)) ~ ++(k(" ")) ~ k("extends ") ~ __ ~
          named("e" -> ?(TraversesIncomplete.RootedIncomplete))
      }
  }

  private def ClassPartialExtends = {
    grammar("ClassPartialExtends")
      .payload(JavascriptPayloadType.ClassDefinition) {
        k("class") ~ named("name" -> ?(Ident.DefinitionName)) ~ ++(k(" ")) ~ ExtendsAll
      }
  }

  def ClassIncomplete = {
    grammar("ClassIncomplete").or(
      ClassExtendsIncomplete,
      ClassPartialExtends)
  }
}

object Classes extends GrammarHelpers with CollectionHelpers {

  def MethodDefinition = {
    grammar("MethodDefinition")
      .payload(JavascriptPayloadType.MethodDefinition) {
        named("m" -> Ident.IdentifierExpression) ~ __ ~
          named("args" -> Functions.FunctionArguments) ~ __ ~
          MaybeBrackets
      }
  }

  private def ClassPropertyValue = {
    grammar("ClassPropertyValue")
      .single("r") {
        named("r" -> ?(Expressions.BasicExpression))
      }
  }

  def ClassProperty = {
    grammar("ClassProperty")
      .payload(JavascriptPayloadType.ClassProperty) {
        named("p" -> Ident.IdentifierExpression) ~ __ ~
          k("=") ~ __ ~
          named("v" -> ClassPropertyValue)
      }
  }

  private def ClassExtends = {
    grammar("ClassExtends")
      .single("r") {
        ++(k(" ")) ~ k("extends ") ~ __ ~ named("r" -> Traverses.RootedExpression)
      }
  }

  def ClassDefinition = {
    grammar("ClassDefinition")
      .payload(JavascriptPayloadType.ClassDefinition) {
        k("class") ~
          named("name" -> ?(Ident.DefinitionName)) ~
          named("e" -> ?(ClassExtends)) ~ __ ~
          MaybeBrackets
      }
  }

  def SuperExpression = {
    grammar("SuperExpression")
      .payload(JavascriptPayloadType.Super) {
        k("super")
      }
  }

  def ThisExpression = {
    grammar("ThisExpression")
      .payload(JavascriptPayloadType.This) {
        k("this")
      }
  }

  private def InstanceArgsInner = {
    nonEmptyListGrammar("InstanceArgsInner", Expressions.Expression)
  }

  private def InstanceArgs = {
    grammar("InstanceArgs")
      .single("inner || []") {
        k("(") ~ named("inner" -> ?(InstanceArgsInner)) ~ k(")")
      }
  }

  def InstanceExpression = {
    grammar("InstanceExpression")
      .payload(JavascriptPayloadType.Instance) {
        k("new") ~ ++(k(" ")) ~ named("ref" -> Ident.RefOrIdent) ~ __ ~ named("args" -> ?(InstanceArgs))
      }
  }

}
