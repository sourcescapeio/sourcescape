package models.query.grammar.ruby

import models.query.grammar._
import Base._
import models.RepoCollectionIntent.Collect

object Traverses extends GrammarHelpers with CollectionHelpers {
  // Const
  def ConstTraverse = {
    grammar("ConstTraverse")
      .single("name") {
        k("::") ~ named("name" -> Ident.ConstIdentifier)
      }
  }

  private def ConstChain = {
    grammar("ConstChain")
      .payload(RubyPayloadType.Const) {
        named("base" -> Ident.ConstIdentifier) ~ named("traverses" -> *(ConstTraverse))
      }
  }

  private def ConstChainWithCBase = {
    grammar("ConstChainWithCBase")
      .payload(RubyPayloadType.Const) {
        named("traverses" -> ++(ConstTraverse)) // assumed cbase on client side? this is kind of nasty
      }
  }

  // Sends

  private def SendArgsInner = {
    nonEmptyListGrammar("SendArgsInner", Expressions.BasicExpression)
  }

  private def SendArgs = {
    grammar("SendArgs")
      .single("args") {
        k("(") ~ named("args" -> ?(SendArgsInner)) ~ k(")")
      }
  }

  private def SendRootExpression = {
    grammar("SendRootExpression")
      .payload(RubyPayloadType.SendRoot) {
        named("name" -> Ident.SendIdentifier) ~ named("args" -> ?(SendArgs))
      }
  }

  def SendTraverse = {
    grammar("SendTraverse")
      .dict() {
        named("name" -> Ident.SendIdentifier) ~ named("args" -> ?(SendArgs))
      }
  }

  private def SendTraverseList = {
    grammar("SendTraverseList")
      .single("a") {
        k(".") ~ named("a" -> nonEmptyListGrammar("SendTraverseListInner", SendTraverse, divider = k(".")))
      }
  }

  def ConstExpression = {
    grammar("ConstExpression")
      .or(
        ConstChainWithCBase,
        ConstChain)
  }

  def RootExpression = {
    grammar("RootExpression")
      .or(
        Base.RefExpression,
        Ident.AnyIdentifier,
        ConstExpression, // must be higher priority
        SendRootExpression)
  }

  def RootedExpression = {
    grammar("RootedExpression")
      .payload(RubyPayloadType.RootedExpression, "root" -> "root", "traverses" -> "traverses || []") {
        named("root" -> RootExpression) ~ named("traverses" -> ?(SendTraverseList))
      }
  }

  /**
   *  Exports
   */
  def ConstPrefixTraverseList = {
    grammar("ConstPrefixTraverseList")
      .payload(RubyPayloadType.TraverseList, "const_traverses" -> "const_traverses", "send_traverses" -> "send_traverses || []") {
        named("const_traverses" -> ++(ConstTraverse)) ~ named("send_traverses" -> ?(SendTraverseList))
      }
  }

  def TraverseList = {
    grammar("TraverseList")
      .payload(RubyPayloadType.TraverseList) {
        named("send_traverses" -> SendTraverseList)
      }
  }

  def AppendArg = {
    grammar("AppendArg")
      .payload(RubyPayloadType.AppendArg) {
        named("send_args" -> SendArgs)
      }
  }
}

object TraversesIncomplete extends GrammarHelpers with CollectionHelpers {
  private def IncompleteSendArgs = {
    incompleteListGrammar("IncompleteSendArgs", pre = "(", divider = ",")(
      Expressions.BasicExpression,
      ExpressionsIncomplete.AnyExpression)
  }

  // resplice
  private def IncompleteSendTraverseBadArgs = {
    grammar("IncompleteSendTraverseBadArgs")
      .dict() {
        named("name" -> Ident.SendIdentifier) ~ named("args" -> IncompleteSendArgs)
      }
  }

  def IncompleteSendRoot = {
    grammar("IncompleteSendRoot")
      .payload(RubyPayloadType.SendRoot) {
        named("name" -> Ident.SendIdentifier) ~ named("args" -> IncompleteSendArgs)
      }
  }

  private def IncompleteTraverseList = {
    incompleteListGrammar("IncompleteTraverseList", pre = ".", divider = ".")(
      Traverses.SendTraverse,
      IncompleteSendTraverseBadArgs)
  }

  def IncompleteRootedExpression = {
    grammar("IncompleteRootedExpression")
      .payload(RubyPayloadType.RootedExpression, "root" -> "root", "traverses" -> "traverses || []") {
        named("root" -> Traverses.RootExpression) ~ named("traverses" -> ?(IncompleteTraverseList))
      }
  }

  def IncompleteConstExpression = {
    grammar("IncompleteConstExpression")
      .single("inner") {
        named("inner" -> Traverses.ConstExpression) ~ k(":") ~ ?(k(":"))
      }
  }

  def IncompleteSendTraverseList = {
    grammar("IncompleteSendTraverseList")
      .payload(RubyPayloadType.TraverseList, "const_traverses" -> "[]", "send_traverses" -> "send_traverses || []") {
        named("send_traverses" -> ?(IncompleteTraverseList))
      }
  }

  def IncompleteSendTraverseListWithConstPrefix = {
    grammar("IncompleteSendTraverseListWithConstPrefix")
      .payload(RubyPayloadType.TraverseList, "const_traverses" -> "[]", "send_traverses" -> "send_traverses || []") {
        named("const_traverses" -> *(Traverses.ConstTraverse)) ~ named("send_traverses" -> ?(IncompleteTraverseList))
      }
  }

  def IncompleteConstTraverse = {
    grammar("IncompleteConstTraverse")
      .payload(RubyPayloadType.TraverseList, "const_traverses" -> "[]", "send_traverses" -> "[]") {
        k(":") ~ ?(k(":"))
      }
  }
}
