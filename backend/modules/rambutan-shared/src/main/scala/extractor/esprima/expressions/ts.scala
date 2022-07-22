package models.extractor.esprima

import models.CodeRange
import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object TS {
  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L1308
  // v as string
  private def TSAsExpression = {
    node("TSAsExpression") ~
      tup("expression" -> Expressions.expression)
    // tup("typeAnnotation" -> TypeNode)
  }

  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L848
  // v.data?
  private def ChainExpression = {
    node("ChainExpression") ~
      tup("expression" -> Expressions.expression)
  }

  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L1527
  // v.data!
  private def TSNonNullExpression = {
    node("TSNonNullExpression") ~
      tup("expression" -> Expressions.expression)
  }

  def expressions = TSAsExpression | ChainExpression | TSNonNullExpression

}