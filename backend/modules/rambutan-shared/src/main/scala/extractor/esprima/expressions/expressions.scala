package models.extractor.esprima

import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Expressions {

  def SequenceExpression = {
    node("SequenceExpression") ~
      tup("expressions" -> list(Expressions.expression))
  } mapExtraction {
    case (context, codeRange, expressions) => {
      ExpressionWrapper(
        SequenceNode(Hashing.uuid, codeRange),
        codeRange,
        expressions,
        Nil,
        Nil)
    }
  }

  type ExpressionType = ChainedExtractor[ESPrimaContext, ExpressionWrapper[ESPrimaNodeBuilder]]
  def expression: ExpressionType = {
    Functions.expressions |
      Basic.expressions | Terminal.expressions | Variables.expressions |
      Classes.expressions | Collections.expressions | SequenceExpression |
      JSX.expressions | TS.expressions | Templates.TaggedTemplateExpression
  }
}
