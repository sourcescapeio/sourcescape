package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._

object Exceptions {

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#rescue-body
  // TODO: we don't handle this at all except to pass thru
  private def ResBody = {
    node("resbody") ~
      tup("children" -> ordered3(opt(Expressions.Expression), opt(Expressions.Expression), Expressions.Expression)) mapExtraction {
        case (_, codeRange, (catches, assigns, inner)) => {
          inner
        }
      }
  }

  private def Rescue = {
    node("rescue") ~
      tup("children" -> ordered1(Expressions.Expression).and {
        list(either(ResBody, opt(Expressions.Expression))) // need to deal with else!
      }) mapExtraction {
        case (context, codeRange, (body, rescues)) => {

          val rescueNode = RescueNode(Hashing.uuid, codeRange)

          val flattenedRescues = rescues flatMap {
            case Left(rbody) => Some(rbody)
            case Right(s)    => s
          }

          ExpressionWrapper(
            rescueNode,
            codeRange,
            body :: flattenedRescues,
            Nil,
            Nil)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#ensure-statement
  private def Ensure = {
    node("ensure") ~
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (testBlock, ensuredBlock)) => {
          val ensureNode = EnsureNode(Hashing.uuid, codeRange)

          val edges = List(
            CreateEdge(ensureNode, AnyNode(testBlock.node), RubyEdgeType.EnsureTest).edge,
            CreateEdge(ensureNode, AnyNode(testBlock.node), RubyEdgeType.Ensure).edge)

          ExpressionWrapper(
            ensureNode,
            codeRange,
            testBlock :: ensuredBlock :: Nil,
            Nil,
            edges)
        }
      }
  }

  def expressions = Rescue | Ensure
}

