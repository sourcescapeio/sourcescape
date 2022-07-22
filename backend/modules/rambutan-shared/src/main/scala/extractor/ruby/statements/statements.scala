package models.extractor.ruby

import models.index.ruby._
import models.extractor._
import silvousplay.imports._

object Statements {

  private def AsStatement[T <: RubyNodeBuilder](expression: Extractor[RubyContext, ExpressionWrapper[T]]) = {
    expression.mapExtraction {
      case (_, _, v) => {
        StatementWrapper(
          v.codeRange,
          v :: Nil,
          Nil)
      }
    }
  }

  def Statement: Extractor[RubyContext, StatementWrapper] = {
    AsStatement(Expressions.Expression)
  }
}
