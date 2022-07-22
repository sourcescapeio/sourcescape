package models.extractor.ruby

import models.index.ruby._
import models.extractor._
import silvousplay.imports._

object Methods {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#formal-arguments
  private def Args = {
    node("args")
    // tup("children" -> sequence(Extractor.str)) // fix
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#auto-expanding-proc-argument-19
  private def ProcArg0 = {
    node("procarg0")
  }

  private def Block = {
    node("block") ~
      tup("children" -> ordered3(Expressions.Expression, either(Args, ProcArg0), opt(Statements.Statement))) mapBoth {
        case (context, codeRange, (base, _, block)) => {
          // TODO: args should be injected into context
          // Args should also be used for block node
          val blockNode = BlockNode(Hashing.uuid, codeRange)
          val nextContext = context.pushCurrentMethod(blockNode)
          val extraction = ExpressionWrapper(
            blockNode,
            codeRange,
            base :: block.toList.flatMap(_.allExpressions),
            Nil,
            Nil // should inject contains
          )

          (nextContext, extraction)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#instance-methods
  private def InstanceDef = {
    node("def") ~
      tup("children" -> ordered3(Extractor.str, Args, opt(Statements.Statement))) mapBoth {
        case (context, codeRange, (name, _, block)) => {
          val defNode = DefNode(Hashing.uuid, codeRange, name)

          val nextContext = context.pushCurrentMethod(defNode)
          val extraction = ExpressionWrapper(
            defNode,
            codeRange,
            block.toList.flatMap(_.allExpressions),
            Nil,
            Nil)
          (nextContext, extraction)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#singleton-methods
  private def SingletonRoot = Expressions.Expression // Classes.Self | Vars.LVar
  // TODO: want to do cooler shit
  // https://github.com/autolab/Autolab/blob/a7e70ac/app/controllers/assessment/grading.rb#L467
  private def SingletonDef = {
    node("defs") ~
      tup("children" -> ordered4(SingletonRoot, Extractor.str, Args, opt(Statements.Statement))) mapBoth {
        case (context, codeRange, (_, name, _, body)) => {
          // TODO: replace with singleton specific
          val defNode = DefNode(Hashing.uuid, codeRange, name)
          val nextContext = context.pushCurrentMethod(defNode)
          val extraction = ExpressionWrapper(
            defNode,
            codeRange,
            body.toList.flatMap(_.allExpressions),
            Nil,
            Nil)
          (nextContext, extraction)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#return
  private def Return = {
    node("return") ~
      tup("children" -> ordered1(opt(Expressions.Expression))) mapExtraction {
        case (context, codeRange, stuff) => {
          val retNode = ReturnNode(Hashing.uuid, codeRange)

          val maybeMethod = context.currentMethod.map { m =>
            CreateEdge(m, retNode, RubyEdgeType.MethodReturn).edge
          }

          val maybeReturn = stuff.map { s =>
            CreateEdge(retNode, AnyNode(s.node), RubyEdgeType.Return).edge
          }

          ExpressionWrapper(
            retNode,
            codeRange,
            stuff.toList,
            Nil,
            List(maybeMethod, maybeReturn).flatten)
        }
      }
  }

  private def Yield = {
    node("yield") ~
      tup("children" -> ordered1(opt(Expressions.Expression))) mapExtraction {
        case (context, codeRange, stuff) => {
          val yieldNode = YieldNode(Hashing.uuid, codeRange)

          val maybeMethod = context.currentMethod.map { m =>
            CreateEdge(m, yieldNode, RubyEdgeType.MethodYield).edge
          }

          val maybeYield = stuff.map { s =>
            CreateEdge(yieldNode, AnyNode(s.node), RubyEdgeType.Yield).edge
          }

          ExpressionWrapper(
            yieldNode,
            codeRange,
            stuff.toList,
            Nil,
            List(maybeMethod, maybeYield).flatten)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#passing-expression-as-block
  // TODO: not quite sure what to do with this
  // https://blog.appsignal.com/2018/09/04/ruby-magic-closures-in-ruby-blocks-procs-and-lambdas.html
  private def BlockPass = {
    node("block_pass") mapExtraction {
      case (_, codeRange, pass) => {
        ExpressionWrapper(
          BlockPassNode(Hashing.uuid, codeRange),
          codeRange,
          Nil,
          Nil,
          Nil)
      }
    }
  }

  def expressions = Return | Yield | InstanceDef | SingletonDef | Block | BlockPass

}