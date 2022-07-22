package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Collections {
  private def Pair = {
    node("pair") ~
      tup("children" -> ordered2(either(Literals.Sym, Literals.Str), Expressions.Expression)) mapExtraction {
        case (_, codeRange, (Left(a), b)) => {
          val pairNode = PairNode(Hashing.uuid, codeRange, a.node.graphName)

          val aEdge = CreateEdge(pairNode, a.node, RubyEdgeType.PairKey).edge // useful?
          val bEdge = CreateEdge(pairNode, AnyNode(b.node), RubyEdgeType.PairValue).edge

          ExpressionWrapper(
            pairNode,
            codeRange,
            a :: b :: Nil,
            Nil,
            aEdge :: bEdge :: Nil)
        }
        case (_, codeRange, (Right(a), b)) => {
          val pairNode = PairNode(Hashing.uuid, codeRange, a.node.graphName)

          val aEdge = CreateEdge(pairNode, a.node, RubyEdgeType.PairKey).edge // useful?
          val bEdge = CreateEdge(pairNode, AnyNode(b.node), RubyEdgeType.PairValue).edge

          ExpressionWrapper(
            pairNode,
            codeRange,
            a :: b :: Nil,
            Nil,
            aEdge :: bEdge :: Nil)
        }
      }
  }

  def Array = {
    node("array") ~
      tup("children" -> list(Expressions.Expression)) mapExtraction {
        case (context, codeRange, children) => {
          val arrayNode = ArrayNode(Hashing.uuid, codeRange)
          val arrayEdges = children.zipWithIndex.map {
            case (c, idx) => CreateEdge(arrayNode, AnyNode(c.node), RubyEdgeType.ArrayElement).indexed(idx)
          }
          ExpressionWrapper(
            arrayNode,
            codeRange,
            children,
            Nil,
            arrayEdges)
        }
      }
  }

  def hashType(name: String) = {
    node(name) ~
      tup("children" -> list(Pair)) mapExtraction {
        case (_, codeRange, pairs) => {
          val hashNode = HashNode(Hashing.uuid, codeRange)
          val hashEdges = pairs.map { p =>
            val baseEdge = CreateEdge(hashNode, p.node, RubyEdgeType.HashElement)
            p.node.graphName match {
              case Some(n) => baseEdge.named(n)
              case _       => baseEdge.edge
            }
          }

          ExpressionWrapper(
            hashNode,
            codeRange,
            pairs,
            Nil,
            hashEdges)
        }
      }
  }

  def Hash = {
    hashType("hash")
  }

  def expressions = Hash | Array

}
