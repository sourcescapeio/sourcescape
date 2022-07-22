package models.extractor.ruby

import models.extractor._
import models.index.ruby._
import silvousplay.imports._
import scalaz.syntax.const
import models.index.ruby.RubyNodeType.SNull

object Vars {

  private def IRange = {
    node("irange") ~ 
      tup("children" -> ordered2(either(Literals.Int, Expressions.Expression), opt(either(Literals.Int, Expressions.Expression)))) mapExtraction {
        case (_, codeRange, (start, end)) => {
          val startIdx = start match {
            case Left(s) => s.node.value
            case _ => 0
          }
          val maybeEnd = end match {
            case Some(Left(e)) => Some(e.node.value)
            case _ => None
          }          
          ExpressionWrapper.single(RangeNode(Hashing.uuid, codeRange, startIdx, maybeEnd, exclusive = false))
        }
      }
  }

  private def ERange = {
    node("erange") ~
      tup("children" -> ordered2(either(Literals.Int, Expressions.Expression), opt(either(Literals.Int, Expressions.Expression)))) mapExtraction {
        case (_, codeRange, (start, end)) => {
          val startIdx = start match {
            case Left(s) => s.node.value
            case _ => 0
          }
          val maybeEnd = end match {
            case Some(Left(e)) => Some(e.node.value)
            case _ => None
          }
          ExpressionWrapper.single(RangeNode(Hashing.uuid, codeRange, startIdx, maybeEnd, exclusive = true))
        }
      }    
  }

  private def CanIndex = Expressions.Expression // Literals.Literal | IRange | ERange | 

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#indexing
  // TODO: indexing is fluffy
  // TODO: index can be expression << but we want special extractors for CanIndex
  // is this like member access (as in js?)
  def Index = {
    node("index") ~
      tup("children" -> ordered2(Expressions.Expression, opt(CanIndex))) mapExtraction {
        case (_, codeRange, (base, index)) => {
          // val name = index.node.name
          val indexNode = IndexNode(Hashing.uuid, codeRange, "")
          val indexEdge = CreateEdge(indexNode, AnyNode(base.node), RubyEdgeType.Index).edge

          ExpressionWrapper(
            indexNode,
            codeRange,
            base :: Nil,
            Nil,
            indexEdge :: Nil)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#kwargs
  private def Kwargs = {
    Collections.hashType("kwargs")
  }

  // ordered3(opt(Expression), Extractor.str, Callable)
  def Send = {
    node("send") ~
      tup("children" -> ordered2(opt(Expressions.Expression), Extractor.str).and {
        either(ordered1(Kwargs), list(Expressions.Expression))
      }) mapExtraction {
        case (_, codeRange, ((maybeA, operator), rest)) => {
          // sendNode
          val sendNode = SendNode(Hashing.uuid, codeRange, operator)
          val (maybeNull, aEdge) = maybeA match {
            case Some(a) => {
              (None, CreateEdge(sendNode, AnyNode(a.node), RubyEdgeType.SendObject).named(operator))
            }
            case None => {
              val snull = SNullNode(Hashing.uuid, codeRange) // eww code range
              (Some(snull), CreateEdge(sendNode, snull, RubyEdgeType.SendObject).named(operator))
            }
          }

          val (restEdges, restExpressions) = rest match {
            case Right(callables) => {
              val edges = callables.zipWithIndex.map {
                case (b, idx) => CreateEdge(sendNode, AnyNode(b.node), RubyEdgeType.SendArg).indexed(idx)
              }

              (edges, callables)
            }
            case Left(kwargs) => {
              val edge = CreateEdge(sendNode, AnyNode(kwargs.node), RubyEdgeType.SendArg).indexed(0)

              (edge :: Nil, kwargs :: Nil)
            }
          }

          ExpressionWrapper(
            sendNode,
            codeRange,
            maybeA.toList ++ restExpressions,
            maybeNull.toList,
            aEdge :: restEdges)
        }
      }
  }

  //https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#constant
  def Const: Extractor[RubyContext, ExpressionWrapper[ConstNode]] = {
    // deal with scope and top-level
    node("const") ~
      tup("children" -> ordered2(opt(either(Const, CBase)), Extractor.str)) mapExtraction {
        case (context, codeRange, (scope, name)) => {
          val constNode = ConstNode(Hashing.uuid, codeRange, name)
          val (maybeNull, edge) = scope match {
            case None => {
              val cnull = CNullNode(Hashing.uuid, codeRange)
              (Some(cnull), CreateEdge(constNode, cnull, RubyEdgeType.Const).named(name))
            }
            case Some(Left(recur)) => {
              (None, CreateEdge(constNode, recur.node, RubyEdgeType.Const).named(name))
            }
            case Some(Right(cbase)) => {
              (None, CreateEdge(constNode, cbase.node, RubyEdgeType.Const).named(name))
            }
          }

          ExpressionWrapper(
            constNode,
            codeRange,
            scope.toList.map(_.merge),
            maybeNull.toList,
            edge :: Nil)
        }
      }
  }

  private def CBase = {
    node("cbase") mapExtraction {
      case (context, codeRange, _) => {
        ExpressionWrapper.single(CBaseNode(Hashing.uuid, codeRange))
      }
    }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#to-local-variable
  private def LocalAssign = {
    node("lvasgn") ~
      tup("children" -> ordered2(Extractor.str, opt(Expressions.Expression))) mapBoth {
        case (context, codeRange, (name, maybeExp)) => {
          // if we reassign, it's essentially a new variable
          val lvar = LVarNode(Hashing.uuid, codeRange, name)
          val nextContext = context.pushLocalVar(name, lvar)

          val assignmentEdge = maybeExp.map(exp => CreateEdge(lvar, AnyNode(exp.node), RubyEdgeType.Assignment).edge)

          // just add to context and push
          val wrapper = ExpressionWrapper(
            lvar,
            codeRange,
            maybeExp.toList,
            Nil,
            assignmentEdge.toList)

          (nextContext, wrapper)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#to-instance-variable
  // TODO: this scope is completely broken. we need to pass context outside
  private def InstanceAssign = {
    node("ivasgn") ~
      tup("children" -> ordered2(Extractor.str, opt(Expressions.Expression))) mapBoth {
        case (context, codeRange, (name, maybeExp)) => {
          // if we reassign, it's essentially a new variable
          val ivar = IVarNode(Hashing.uuid, codeRange, name)
          val nextContext = context.pushInstanceVar(name, ivar)

          val assignmentEdge = maybeExp.map(exp => CreateEdge(ivar, AnyNode(exp.node), RubyEdgeType.Assignment).edge)

          // just add to context and push
          val wrapper = ExpressionWrapper(
            ivar,
            codeRange,
            maybeExp.toList,
            Nil,
            assignmentEdge.toList)

          (nextContext, wrapper)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#to-class-variable
  // TODO (!!!!!): This is just using instance variables!!!!
  private def ClassAssign = {
    node("cvasgn") ~
      tup("children" -> ordered2(Extractor.str, opt(Expressions.Expression))) mapBoth {
        case (context, codeRange, (name, maybeExp)) => {
          // if we reassign, it's essentially a new variable
          val ivar = IVarNode(Hashing.uuid, codeRange, name)
          val nextContext = context.pushInstanceVar(name, ivar)

          val assignmentEdge = maybeExp.map(exp => CreateEdge(ivar, AnyNode(exp.node), RubyEdgeType.Assignment).edge)

          // just add to context and push
          val wrapper = ExpressionWrapper(
            ivar,
            codeRange,
            maybeExp.toList,
            Nil,
            assignmentEdge.toList)

          (nextContext, wrapper)
        }
      }
  }  

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#to-global-variable
  // TODO: this scope is completely broken. we need to pass context outside
  // TODO (!!!!!): This is just using instance variables!!!!
  private def GlobalAssign = {
    node("gvasgn") ~
      tup("children" -> ordered2(Extractor.str, opt(Expressions.Expression))) mapBoth {
        case (context, codeRange, (name, maybeExp)) => {
          // if we reassign, it's essentially a new variable
          val ivar = IVarNode(Hashing.uuid, codeRange, name)
          val nextContext = context.pushInstanceVar(name, ivar)

          val assignmentEdge = maybeExp.map(exp => CreateEdge(ivar, AnyNode(exp.node), RubyEdgeType.Assignment).edge)

          // just add to context and push
          val wrapper = ExpressionWrapper(
            ivar,
            codeRange,
            maybeExp.toList,
            Nil,
            assignmentEdge.toList)

          (nextContext, wrapper)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#to-constant
  // shrug?
  private def ConstantBase = LVar | CBase
  private def ConstantAssign = {
    node("casgn") ~ 
      tup("children" -> ordered3(opt(ConstantBase), Extractor.str, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (_, _, exp)) => {
          // Can do basic const static analysis
          exp
          // val ivar = IVarNode(Hashing.uuid, codeRange, name)
          // val nextContext = context.pushInstanceVar(name, ivar)

          // val assignmentEdge = maybeExp.map(exp => CreateEdge(ivar, AnyNode(exp.node), RubyEdgeType.Assignment).edge)

          // // just add to context and push
          // val wrapper = ExpressionWrapper(
          //   ivar,
          //   codeRange,
          //   maybeExp.toList,
          //   Nil,
          //   assignmentEdge.toList)

          // (nextContext, wrapper)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#multiple-left-hand-side
  // TODO: deal with this
  private def MLHS = {
    node("mlhs") mapExtraction {
      case (context, codeRange, _) => {
        // blah
        ExpressionWrapper.single(ReferenceNode(Hashing.uuid, codeRange, ""))
      }
    }
  }

  // pass through
  private def MultiAssign = {
    node("masgn") ~ 
      tup("children" -> ordered2(MLHS, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (_, exp)) => exp
      }
  }


  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#binary-operator-assignment
  // TODO: need to deal with this
  def OpAsgn = {
    node("op_asgn") ~ 
      tup("children" -> ordered3(Expressions.Expression, Extractor.str, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, op, b)) => {
          //a = a op b
          ExpressionWrapper(
            a.node,
            codeRange,
            a :: b :: Nil,
            Nil,
            Nil,
          )
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#logical-operator-assignment
  // TODO: need to deal with this
  def OrAsgn = {
    node("or_asgn") ~ 
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, b)) => {
          //a = a op b
          ExpressionWrapper(
            a.node,
            codeRange,
            a :: b :: Nil,
            Nil,
            Nil,
          )
        }
      }
  }

  def AndAsgn = {
    node("and_asgn") ~ 
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, b)) => {
          //a = a op b
          ExpressionWrapper(
            a.node,
            codeRange,
            a :: b :: Nil,
            Nil,
            Nil,
          )
        }
      }
  }


  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#indexing
  // TODO: need to deal with this
  def IndexAsgn = {
    node("indexasgn") ~ 
      tup("children" -> ordered2(Expressions.Expression, Expressions.Expression)) mapExtraction {
        case (context, codeRange, (a, b)) => {
          //a = a op b
          ExpressionWrapper(
            a.node,
            codeRange,
            a :: b :: Nil,
            Nil,
            Nil,
          )
        }
      }
  }

  //https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#local-variable
  def LVar = {
    node("lvar") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, name) => {
          val referenceNode = ReferenceNode(Hashing.uuid, codeRange, name)

          val maybeEdge = context.getLocalVar(name).map { lvar =>
            CreateEdge(referenceNode, lvar, RubyEdgeType.Reference).edge
          }

          ExpressionWrapper(
            referenceNode,
            codeRange,
            Nil,
            Nil,
            maybeEdge.toList)
        }
      }
  }

  //https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#instance-variable
  def IVar = {
    node("ivar") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, name) => {
          val referenceNode = ReferenceNode(Hashing.uuid, codeRange, name)

          val maybeEdge = context.getInstanceVar(name).map { ivar =>
            CreateEdge(referenceNode, ivar, RubyEdgeType.Reference).edge
          }

          ExpressionWrapper(
            referenceNode,
            codeRange,
            Nil,
            Nil,
            maybeEdge.toList)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#global-variable
  def CVar = {
    node("cvar") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, name) => {
          val referenceNode = ReferenceNode(Hashing.uuid, codeRange, name)

          val maybeEdge = context.getInstanceVar(name).map { ivar =>
            CreateEdge(referenceNode, ivar, RubyEdgeType.Reference).edge
          }

          ExpressionWrapper(
            referenceNode,
            codeRange,
            Nil,
            Nil,
            maybeEdge.toList)
        }
      }
  }

  // https://github.com/whitequark/parser/blob/master/doc/AST_FORMAT.md#global-variable
  def GVar = {
    node("gvar") ~
      tup("children" -> ordered1(Extractor.str)) mapExtraction {
        case (context, codeRange, name) => {
          val referenceNode = ReferenceNode(Hashing.uuid, codeRange, name)

          val maybeEdge = context.getInstanceVar(name).map { ivar =>
            CreateEdge(referenceNode, ivar, RubyEdgeType.Reference).edge
          }

          ExpressionWrapper(
            referenceNode,
            codeRange,
            Nil,
            Nil,
            maybeEdge.toList)
        }
      }
  }

  def expressions = Send | ConstantAssign | Const | LocalAssign | LVar | InstanceAssign | IVar | ClassAssign | CVar | GlobalAssign | GVar | MultiAssign | MLHS | Index | IRange | ERange | OpAsgn | OrAsgn | AndAsgn | IndexAsgn
}
