package models.extractor.ruby

import models.index.ruby._
import models.extractor._
import silvousplay.imports._

object Expressions {
  // TODO: there are no statements!

  private def Begin = {
    node("begin") ~
      tup("children" -> sequence(opt(Statements.Statement))) mapExtraction {
        case (_, codeRange, statements) => {
          val beginNode = BeginNode(Hashing.uuid, codeRange)
          ExpressionWrapper(
            beginNode,
            codeRange,
            statements.flatten.flatMap(_.allExpressions),
            Nil,
            Nil)
        }
      }
    //, Statements.Statement)
  }

  private def KwBegin = {
    node("kwbegin") ~
      tup("children" -> sequence(opt(Statements.Statement))) mapExtraction {
        case (_, codeRange, statements) => {
          val beginNode = BeginNode(Hashing.uuid, codeRange)
          ExpressionWrapper(
            beginNode,
            codeRange,
            statements.flatten.flatMap(_.allExpressions),
            Nil,
            Nil)
        }
      }
  }

  def Expression: ChainedExtractor[RubyContext, ExpressionWrapper[RubyNodeBuilder]] = {
    Begin | KwBegin | Vars.expressions | Literals.Literal | Cond.expressions |
      Collections.expressions | Classes.expressions | Methods.expressions | Booleans.expressions |
      Loops.expressions | Misc.expressions | Exceptions.expressions
  }

}
