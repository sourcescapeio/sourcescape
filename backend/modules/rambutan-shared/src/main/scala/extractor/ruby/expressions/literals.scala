package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Literals {
  def Int = {
    node("int") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, item) => {
          ExpressionWrapper.single(IntNode(Hashing.uuid, codeRange, item.toInt))
        }
      }
  }

  def Float = {
    node("float") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, item) => {
          ExpressionWrapper.single(FloatNode(Hashing.uuid, codeRange, item.toDouble))
        }
      }
  }

  def Str = {
    node("str") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, item) => {
          ExpressionWrapper.single(StrNode(Hashing.uuid, codeRange, item))
        }
      }
  }

  def Sym = {
    node("sym") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, item) => {
          ExpressionWrapper.single(SymNode(Hashing.uuid, codeRange, item))
        }
      }
  }

  def RTrue = {
    node("true") mapExtraction {
      case (_, codeRange, _) => ExpressionWrapper.single(KeywordLiteralNode(Hashing.uuid, codeRange, true.toString))
    }
  }

  def RFalse = {
    node("false") mapExtraction {
      case (_, codeRange, _) => ExpressionWrapper.single(KeywordLiteralNode(Hashing.uuid, codeRange, false.toString))
    }
  }

  def RNil = {
    node("nil") mapExtraction {
      case (_, codeRange, _) => ExpressionWrapper.single(KeywordLiteralNode(Hashing.uuid, codeRange, "nil"))
    }
  }

  def Literal: ChainedExtractor[RubyContext, ExpressionWrapper[LiteralBuilder]] = Int | Float | Str | Sym | RTrue | RFalse | RNil
}
