package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Booleans {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#binary-and-or--
  // TODO: we're ignoring the op
  private def And = {
    node("and") ~ 
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, b)) => {
          val andNode = AndNode(Hashing.uuid, codeRange)
          val edges = List(
            CreateEdge(andNode, AnyNode(a.node), RubyEdgeType.LHS).edge,
            CreateEdge(andNode, AnyNode(b.node), RubyEdgeType.RHS).edge
          )
          ExpressionWrapper(
            andNode,
            codeRange,
            a :: b :: Nil,
            Nil,
            edges,
          )
        }
      }
  }
  private def Or = {
    node("or") ~ 
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, b)) => {
          val orNode = OrNode(Hashing.uuid, codeRange)
          val edges = List(
            CreateEdge(orNode, AnyNode(a.node), RubyEdgeType.LHS).edge,
            CreateEdge(orNode, AnyNode(b.node), RubyEdgeType.RHS).edge
          )
          ExpressionWrapper(
            orNode,
            codeRange,
            a :: b :: Nil,
            Nil,
            edges,
          )
        }
      }    
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#unary--not-18
  private def Not = {
    node("not") ~ 
      tup("children" -> ordered1(Expressions.Expression)) mapExtraction {
        case (context, codeRange, a) => {
          val notNode = NotNode(Hashing.uuid, codeRange)
          val edge = CreateEdge(notNode, AnyNode(a.node), RubyEdgeType.LHS).edge

          ExpressionWrapper(
            notNode,
            codeRange,
            a :: Nil,
            Nil,
            edge :: Nil,
          )
        }
      }
  }

  def expressions: Extractor[RubyContext, ExpressionWrapper[RubyNodeBuilder]] = And | Not | Or
  
}