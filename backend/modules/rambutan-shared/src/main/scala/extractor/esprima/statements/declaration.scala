package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._
import models._

object Declaration {
  // TODO: map all the context
  def ClassDeclaration = {
    Classes.extractClass(node("ClassDeclaration"))
  } mapExtraction {
    case (context, codeRange, expWrapper) => {
      StatementWrapper(
        codeRange,
        expWrapper :: Nil,
        Nil)
    }
  }

  def FunctionDeclaration = {
    Functions.extractFunction("FunctionDeclaration")
  } mapExtraction {
    case (context, codeRange, expWrapper) => {
      StatementWrapper(
        codeRange,
        expWrapper :: Nil,
        Nil)
    }
  }

  private def VariableDeclarator = {
    node("VariableDeclarator") ~
      tup("id" -> (Patterns.IdentifierPattern | Patterns.BindingPattern)) ~
      tup("init" -> opt(Expressions.expression))
  } mapExtraction {
    case (context, codeRange, (id, init)) => {
      val expressions = init match {
        case Some(i) => id.apply(0, i.node)
        case _ => id.allIdentifiers.map { id =>
          ExpressionWrapper(id, id.range, Nil, Nil, Nil)
        }
      }
      val wrapper = StatementWrapper(
        codeRange,
        expressions,
        withDefined(init) { i =>
          // inject function name
          val maybeName = expressions.headOption.flatMap(e => Property.computeName(e.node))
          val injectedInit = i.node match {
            case f: FunctionNode if f.name.isEmpty => {
              i.copy(node = f.copy(name = maybeName))
            }
            case _ => i
          }

          StatementWrapper(
            i.codeRange,
            injectedInit :: Nil,
            Nil) :: Nil
        })

      wrapper
    }
  }

  def VariableDeclaration = {
    node("VariableDeclaration") ~
      tup("declarations" -> list(VariableDeclarator)) ~
      tup("kind" -> Extractor.enum("var", "const", "let"))
  } mapBoth {
    case (context, codeRange, (declarations, kind)) => {
      val allIdentifiers = declarations.flatMap(_.allExpressions).flatMap(_.allIdentifiers)

      val nextContext = context.declareIdentifiers(allIdentifiers)
      val wrapper = StatementWrapper(codeRange, Nil, declarations)
      (nextContext, wrapper)
    }
  }

  /**
   * Used in for loops
   */
  private def LoopVariableDeclarator = {
    node("VariableDeclarator") ~
      tup("id" -> (Patterns.IdentifierPattern | Patterns.BindingPattern))
  } mapExtraction {
    case (context, codeRange, id) => {
      id
    }
  }

  def LoopVariableDeclaration = {
    node("VariableDeclaration") ~
      tup("declarations" -> list(LoopVariableDeclarator)) ~
      tup("kind" -> Extractor.enum("var", "const", "let"))
  } mapExtraction {
    case (context, codeRange, (declarations, kind)) => {
      declarations
    }
  }

  /**
   * Ignored TS
   */
  private def TSTypeAliasDeclaration = {
    node("TSTypeAliasDeclaration")
  } mapExtraction {
    case (context, codeRange, _) => StatementWrapper.empty(codeRange)
  }

  private def TSInterfaceDeclaration = {
    node("TSInterfaceDeclaration")
  } mapExtraction {
    case (context, codeRange, _) => StatementWrapper.empty(codeRange)
  }

  private def TSEnumDeclaration = {
    node("TSEnumDeclaration")
  } mapExtraction {
    case (context, codeRange, _) => StatementWrapper.empty(codeRange)
  }

  private def TSImportEqualsDeclaration = {
    node("TSImportEqualsDeclaration")
  } mapExtraction {
    case (context, codeRange, _) => StatementWrapper.empty(codeRange)
  }

  private def TSDeclarations = TSTypeAliasDeclaration | TSInterfaceDeclaration | TSEnumDeclaration | TSImportEqualsDeclaration

  def statements = {
    ClassDeclaration | FunctionDeclaration | VariableDeclaration | TSDeclarations
  }
}