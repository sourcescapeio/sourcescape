package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._
import play.api.libs.json._

object Basic {
  private def unary(in: NodeExtractor[ESPrimaContext, Unit]) = {
    in ~ 
    tup("operator" -> Extractor.str) ~ // do we want to enum restrict operators? do we care? 
    tup("prefix" -> Extractor.bool) ~ 
    tup("argument" -> Expressions.expression)    
  } mapExtraction {
    case (context, codeRange, ((operator, prefix), argument)) => {
      val node = UnaryExpressionNode(
        Hashing.uuid,
        codeRange,
        operator,
        prefix
      )
      val edge = CreateEdge(AnyNode(argument.node), node, ESPrimaEdgeType.BasicExpression).indexed(0)

      ExpressionWrapper(
        node,
        codeRange,
        argument :: Nil,
        Nil,
        edge :: Nil,
      )
    }
  }  

  def UpdateExpression = unary(node("UpdateExpression"))
  def UnaryExpression = unary(node("UnaryExpression"))

  private def binary(in: NodeExtractor[ESPrimaContext, Unit]) = {
    in ~ 
      tup("left" -> Expressions.expression) ~
      tup("right" -> Expressions.expression) ~
      tup("operator" -> Extractor.str)    
  } mapExtraction {
    case (context, codeRange, ((left, right), operator)) => {
      val node = BinaryExpressionNode(Hashing.uuid, codeRange, operator)

      val argEdges = List(
        CreateEdge(AnyNode(left.node), node, ESPrimaEdgeType.BasicExpression).indexed(0),
        CreateEdge(AnyNode(right.node), node, ESPrimaEdgeType.BasicExpression).indexed(1))

      ExpressionWrapper(
        node,
        codeRange,
        left :: right :: Nil,
        Nil,
        argEdges
      )
    }
  }

  def BinaryExpression = binary(node("BinaryExpression"))
  def LogicalExpression = binary(node("LogicalExpression"))

  def ConditionalExpression = {
    node("ConditionalExpression") ~ 
    tup("test" -> Expressions.expression) ~ 
    tup("consequent" -> Expressions.expression) ~ 
    tup("alternate" -> Expressions.expression)    
  } mapExtraction {
    case (context, codeRange, ((test, consequent), alternate)) => {
      val node = ConditionalNode(Hashing.uuid, codeRange)
      val testEdge = CreateEdge(node, AnyNode(test.node), ESPrimaEdgeType.ConditionalTest).edge
      val consequentEdge = CreateEdge(node, AnyNode(consequent.node), ESPrimaEdgeType.ConditionalConsequent).edge
      val altEdge = CreateEdge(node, AnyNode(alternate.node), ESPrimaEdgeType.ConditionalAlternate).edge
      
      ExpressionWrapper(
        node,
        codeRange,
        test :: consequent :: alternate :: Nil,
        Nil,
        testEdge :: consequentEdge :: altEdge :: Nil
      )
    }
  }

  def expressions = {
    BinaryExpression | LogicalExpression | UnaryExpression | UpdateExpression | ConditionalExpression
  }
}