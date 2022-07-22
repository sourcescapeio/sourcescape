package models.extractor.esprima

import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Templates {
  def TaggedTemplateExpression = {
    node("TaggedTemplateExpression") ~
      tup("tag" -> Expressions.expression) ~
      tup("quasi" -> Collections.TemplateLiteral)
  } mapExtraction {
    case (_, codeRange, (tag, quasi)) => {
      ExpressionWrapper.single(TaggedTemplateNode(Hashing.uuid, codeRange))
    }
  }
}
