package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Misc {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#with-interpolation
  private def DStr = {
    node("dstr") mapExtraction {
      case (_, codeRange, _) => {
        ExpressionWrapper.single(DStrNode(Hashing.uuid, codeRange))
      }
    }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#execute-string
  // TODO: No idea what this is
  private def XStr = {
    node("xstr") mapExtraction {
      case (_, codeRange, _) => {
        ExpressionWrapper.single(DStrNode(Hashing.uuid, codeRange))
      }
    }
  }

  def expressions = DStr | XStr
}