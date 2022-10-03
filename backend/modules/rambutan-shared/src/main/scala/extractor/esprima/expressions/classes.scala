package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._

object Classes {

  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L902
  private def Decorator = {
    node("Decorator") ~
      tup("expression" -> Expressions.expression) // LeftHandSideExpression
  }

  val ThisExpression = {
    node("ThisExpression") mapExtraction {
      case (_, codeRange, _) => ExpressionWrapper.single(ThisNode(Hashing.uuid, codeRange))
    }
  }

  val Super = {
    node("Super") mapExtraction {
      case (_, codeRange, _) => ExpressionWrapper.single(SuperNode(Hashing.uuid, codeRange))
    }
  }

  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L673
  type MethodDefinitionType = Extractor[ESPrimaContext, ExpressionWrapper[MethodNode]]
  private def extractMethod(name: String): MethodDefinitionType = {
    node(name) ~
      tup("key" -> Expressions.expression) ~
      tup("value" -> Functions.FunctionExpression) ~
      tup("kind" -> Extractor.enum("method", "constructor", "get", "set")) ~
      tup("computed" -> Extractor.bool) ~
      tup("static" -> Extractor.bool) ~
      tup("decorators" -> list(Decorator), optional = true)
  } mapExtraction {
    case (context, codeRange, (((((key, value), kind), computed), static), decorators)) => {
      val maybeName = Property.computeName(key.node)
      val maybeExp = Property.computeExpression(key)

      val isConstructor = kind =?= "constructor"
      val methodNode = MethodNode(Hashing.uuid, codeRange, computed, static, isConstructor, maybeName)
      val methodFunction = CreateEdge(methodNode, value.node, ESPrimaEdgeType.MethodFunction).edge
      val decoratorEdges = decorators.map { decorator =>
        CreateEdge(methodNode, AnyNode(decorator.node), ESPrimaEdgeType.MethodDecorator).edge
      }

      val methodKey = withDefined(maybeExp) { ex =>
        List(CreateEdge(methodNode, AnyNode(ex.node), ESPrimaEdgeType.MethodKey).edge)
      }
      // method -> key
      // Method -> Value

      ExpressionWrapper(
        methodNode,
        codeRange,
        value :: (decorators ++ maybeExp.toList),
        Nil,
        methodFunction :: (methodKey ++ decoratorEdges))
    }
  }

  // Using statement wrappers
  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L1289
  private def TSAbstractMethodDefinition = {
    extractMethod("TSAbstractMethodDefinition")
  }

  private def MethodDefinition = {
    extractMethod("MethodDefinition")
  }

  private def methods = MethodDefinition | TSAbstractMethodDefinition

  // TODO: consider class property
  // https://github.com/typescript-eslint/typescript-eslint/blob/a8227a6/packages/types/src/ts-estree.ts#L1275
  private def extractProperty(name: String) = {
    node(name) ~
      tup("key" -> Expressions.expression) ~
      tup("value" -> opt(Expressions.expression)) ~
      tup("computed" -> Extractor.bool) ~
      tup("static" -> Extractor.bool)
  } mapExtraction {
    case (context, codeRange, (((key, value), computed), static)) => {
      val maybeName = Property.computeName(key.node)
      val maybeExp = Property.computeExpression(key)

      value.map(_.node) match {
        case Some(f @ FunctionNode(_, _, _, _)) => {
          val methodNode = MethodNode(Hashing.uuid, codeRange, computed, static, false, maybeName)
          val methodFunction = CreateEdge(methodNode, f, ESPrimaEdgeType.MethodFunction).edge

          val methodKey = withDefined(maybeExp) { ex =>
            List(CreateEdge(methodNode, AnyNode(ex.node), ESPrimaEdgeType.MethodKey).edge)
          }

          Left(
            ExpressionWrapper(
              methodNode,
              codeRange,
              value.toList ++ maybeExp.toList,
              Nil,
              methodFunction :: methodKey))
        }
        case _ => {
          val propertyNode = ClassPropertyNode(Hashing.uuid, codeRange, computed, static, maybeName)
          val propertyValue = withDefined(value) { v =>
            CreateEdge(propertyNode, AnyNode(v.node), ESPrimaEdgeType.ClassPropertyValue).edge :: Nil
          }
          val propertyKey = withDefined(maybeExp) { ex =>
            List(CreateEdge(propertyNode, AnyNode(ex.node), ESPrimaEdgeType.ClassPropertyKey).edge)
          }

          Right(
            ExpressionWrapper(
              propertyNode,
              codeRange,
              value.toList ++ maybeExp.toList,
              Nil,
              propertyValue.toList ++ propertyKey))
        }
      }
    }
  }

  private def TSAbstractClassProperty = {
    extractProperty("TSAbstractClassProperty")
  }

  private def ClassProperty = {
    extractProperty("ClassProperty")
  }

  private def properties = ClassProperty | TSAbstractClassProperty

  private def ClassBody = {
    node("ClassBody") ~
      tup("body" -> list(methods or properties))
  }

  def extractClass(in: NodeExtractor[ESPrimaContext, Unit]) = {
    in ~
      tup("id" -> opt(Terminal.Identifier)) ~
      tup("superClass" -> opt(Terminal.Identifier | Variables.MemberExpression | Functions.CallExpression)) ~
      tup("body" -> ClassBody) ~
      tup("decorators" -> list(Decorator), optional = true)
  } mapBoth {
    case (context, codeRange, (((id, superClass), body), decorators)) => {
      val maybeIdent = withDefined(id) { i =>
        Option(i.node.toIdentifier)
      }
      val maybeName = maybeIdent.map(_.name)
      val classNode = ClassNode(Hashing.uuid, codeRange, maybeName)

      val superClassEdge = superClass map { s =>
        CreateEdge(classNode, AnyNode(s.node), ESPrimaEdgeType.SuperClass).edge
      }

      val decoratorEdges = decorators.map { decoratorExp =>
        CreateEdge(classNode, AnyNode(decoratorExp.node), ESPrimaEdgeType.ClassDecorator).edge
      }

      val maybeIdentEdge = withDefined(maybeIdent) { ident =>
        List(CreateEdge(ident, classNode, ESPrimaEdgeType.Declare).edge)
      }

      val onlyMethodBody = body.flatMap(_.left.toOption) ++ body.flatMap(_.toOption).flatMap(_.left.toOption)

      val methodEdges = onlyMethodBody.map(_.node).map {
        case methodExpression if methodExpression.constructor => {
          CreateEdge(classNode, methodExpression, ESPrimaEdgeType.Constructor).edge
        }
        case methodExpression => {
          CreateEdge(classNode, methodExpression, ESPrimaEdgeType.Method).named(methodExpression.name.getOrElse(""))
        }
      }

      val onlyPropertiesBody = body.flatMap(_.toOption).flatMap(_.toOption)

      val propertyEdges = onlyPropertiesBody.map(_.node).map {
        case propertyExpression => {
          CreateEdge(classNode, propertyExpression, ESPrimaEdgeType.ClassProperty).named(propertyExpression.name.getOrElse(""))
        }
      }

      // inject methods
      (
        maybeIdent.foldLeft(context)(_ declareIdentifier _),
        ExpressionWrapper(
          classNode,
          codeRange,
          superClass.toList ++ onlyMethodBody ++ onlyPropertiesBody ++ decorators,
          maybeIdent.toList,
          superClassEdge.toList ++ methodEdges ++ propertyEdges ++ decoratorEdges ++ maybeIdentEdge))
    }
  }

  def ClassExpression = {
    extractClass(node("ClassExpression"))
  }

  def NewExpression = {
    node("NewExpression") ~
      tup("callee" -> Expressions.expression) ~
      tup("arguments" -> list(Functions.ArgumentListElement))
  } mapExtraction {
    case (context, codeRange, (callee, arguments)) => {
      val instanceNode = InstantiationNode(Hashing.uuid, codeRange)
      val instanceEdge = CreateEdge(instanceNode, AnyNode(callee.node), ESPrimaEdgeType.Class).edge

      val argumentEdges = arguments.zipWithIndex.map {
        case (arg, idx) => {
          CreateEdge(AnyNode(arg.node), instanceNode, ESPrimaEdgeType.Argument).indexed(idx)
        }
      }

      ExpressionWrapper(
        instanceNode,
        codeRange,
        callee :: arguments,
        Nil,
        instanceEdge :: argumentEdges)
    }
  }

  def expressions = {
    ThisExpression | Super | NewExpression | ClassExpression
  }
}
