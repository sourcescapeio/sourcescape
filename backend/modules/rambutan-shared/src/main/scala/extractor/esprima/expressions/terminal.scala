package models.extractor.esprima

import silvousplay.imports._
import models.extractor._
import models.index.esprima._

object Terminal {
  val Literal = {
    node("Literal") ~
      tup("raw" -> Extractor.str) ~
      tup("value" -> Extractor.js)
  } mapExtraction {
    case (_, codeRange, (raw, value)) => {
      ExpressionWrapper.single(LiteralNode(Hashing.uuid, codeRange, raw, value))
    }
  }

  def extractIdentifier(context: ESPrimaContext, codeRange: models.CodeRange, name: String) = {
    val node = IdentifierReferenceNode(Hashing.uuid, codeRange, name)

    val maybeEdge = context.findIdentifier(name) map { n =>
      CreateEdge(node, n, ESPrimaEdgeType.Reference).edge
    }

    ExpressionWrapper[IdentifierReferenceNode](node, codeRange, Nil, Nil, maybeEdge.toList)
  }

  val Identifier = {
    node("Identifier") ~
      tup("name" -> Extractor.str)
  } mapExtraction {
    case (context, codeRange, name) => {
      extractIdentifier(context, codeRange, name)
    }
  }

  val expressions = Literal | Identifier
}
