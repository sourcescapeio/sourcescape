package models.query.grammar.javascript

import models.query.grammar._

import Base._
import java.lang.reflect.Member

object TraversesIncomplete extends GrammarHelpers {

  private def IncompleteCallTraverseLastArg = {
    grammar("IncompleteCallTraverseLastArg")
      .single("lastArg") {
        k(",") ~ named("lastArg" -> ?(ExpressionsIncomplete.ExpressionIncomplete))
      }
  }

  private def CallTraverseIncomplete = {
    grammar("CallTraverseIncomplete")
      .payload(
        JavascriptPayloadType.CallTraverse,
        "args" -> "[...(args || []), ...(lastArg ? [lastArg] : [])]") {
          k("(") ~
            named("args" -> ?(Traverses.CallArgs)) ~
            named("lastArg" -> ?(IncompleteCallTraverseLastArg))
        }
  }

  private def MemberTraverseIncomplete = {
    grammar("MemberTraverseIncomplete")
      .payload(JavascriptPayloadType.MemberTraverse) {
        k(".")
      }
  }

  private def TraverseIncomplete: Grammar = {
    grammar("TraverseIncomplete").or(
      MemberTraverseIncomplete,
      CallTraverseIncomplete)
  }

  def TraverseListIncomplete = {
    grammar("TraverseListIncomplete")
      .payload(JavascriptPayloadType.TraverseList, "traverses" -> "[...head, tail]") {
        named("head" -> *(Traverses.Traverse)) ~ named("tail" -> TraverseIncomplete)
      }
  }

  //
  private def RootIncomplete = {
    grammar("RootIncomplete").or(
      Base.RefIncomplete,
      SimpleIncomplete.RequireIncomplete
    // Classes.InstanceExpression
    )
  }

  private def RootExpressionIncomplete = {
    grammar("RootExpressionIncomplete")
      .payload(JavascriptPayloadType.RootedExpression, "root" -> "root", "traverses" -> "[]") {
        named("root" -> RootIncomplete)
      }
  }

  private def RootedListIncomplete = {
    grammar("RootedListIncomplete")
      .payload(
        JavascriptPayloadType.RootedExpression,
        "root" -> "root", "traverses" -> "traverses.concat([last])") {
          named("root" -> Traverses.RootExpression) ~
            named("traverses" -> *(Traverses.Traverse)) ~
            named("last" -> TraverseIncomplete)
        }
  }

  def RootedIncomplete = {
    grammar("RootedIncomplete").or(
      RootedListIncomplete,
      RootExpressionIncomplete)
  }
}

object Traverses extends GrammarHelpers with CollectionHelpers {

  def CallArgs = {
    nonEmptyListGrammar("CallArgs", Expressions.Expression)
  }

  private def CallTraverse = {
    grammar("CallTraverse")
      .payload(JavascriptPayloadType.CallTraverse) {
        k("(") ~ named("args" -> ?(CallArgs)) ~ k(")")
      }
  }

  private def MemberTraverse = {
    grammar("MemberTraverse")
      .payload(JavascriptPayloadType.MemberTraverse) {
        k(".") ~ named("access" -> Ident.FreeIdentifierExpression)
      }
  }

  def Traverse = {
    grammar("Traverse")
      .or(
        CallTraverse,
        MemberTraverse)
  }

  def TraverseList = {
    grammar("TraverseList")
      .payload(JavascriptPayloadType.TraverseList) {
        named("traverses" -> ++(Traverse))
      }
  }

  def RootExpression = {
    grammar("RootExpression")
      .or(
        Base.RefExpression,
        Classes.SuperExpression,
        Classes.ThisExpression,
        Simple.RequireExpression,
        Classes.InstanceExpression,
        Ident.IdentifierExpression)
  }

  def RootedExpression = {
    grammar("RootedExpression")
      .payload(JavascriptPayloadType.RootedExpression) {
        named("root" -> RootExpression) ~ named("traverses" -> *(Traverses.Traverse))
      }
  }

}