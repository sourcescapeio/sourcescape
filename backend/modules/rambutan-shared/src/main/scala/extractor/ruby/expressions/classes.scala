package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Classes {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#self
  def Self = {
    node("self") mapExtraction {
      case (context, codeRange, _) => {
        ExpressionWrapper.single(SelfNode(Hashing.uuid, codeRange))
      }
    }
  }

  //
  private def Super = {
    node("super") mapExtraction {
      case (context, codeRange, _) => {
        val superNode = SuperNode(Hashing.uuid, codeRange)
        ExpressionWrapper.single(superNode)
      }
    }
  }

  private def ZSuper = {
    node("zsuper") mapExtraction {
      case (context, codeRange, _) => {
        val superNode = SuperNode(Hashing.uuid, codeRange)
        ExpressionWrapper.single(superNode)
      }
    }
  }

  private def CanSuperClass = Vars.Const | Vars.Index

  //https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#class
  private def Class = {
    node("class") ~
      tup("children" -> ordered3(Vars.Const, opt(CanSuperClass), opt(Statements.Statement))) mapExtraction {
        case (context, codeRange, (constName, superClass, body)) => {
          // TODO: pull full name, maybe?
          val classNode = ClassNode(Hashing.uuid, codeRange, constName.node.graphName)
          // val maybeEdge = withDefined(scope) { s =>
          //   CreateEdge(constNode, s.node, RubyEdgeType.Const).named(name) :: Nil
          // }
          ExpressionWrapper(
            classNode,
            codeRange,
            constName :: superClass.toList ++ body.toList.flatMap(_.allExpressions),
            Nil,
            Nil)
        }
      }
  }

  //https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#module
  def Module = {
    node("module") ~
      tup("children" -> ordered2(Vars.Const, opt(Statements.Statement))) mapExtraction {
        case (context, codeRange, (constName, body)) => {
          // TODO: pull full name, maybe?
          val moduleNode = ModuleNode(Hashing.uuid, codeRange, constName.node.graphName)
          // val maybeEdge = withDefined(scope) { s =>
          //   CreateEdge(constNode, s.node, RubyEdgeType.Const).named(name) :: Nil
          // }
          ExpressionWrapper(
            moduleNode,
            codeRange,
            constName :: body.toList.flatMap(_.allExpressions),
            Nil,
            Nil)
        }
      }
  }

  def expressions = Self | Super | Class | Module | ZSuper
}
