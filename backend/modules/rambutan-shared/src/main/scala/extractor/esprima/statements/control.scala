package models.extractor.esprima

import models.index.esprima._
import silvousplay.imports._
import models.extractor._
import models._

object Control {

  def LabeledStatement = {
    (node("LabeledStatement") ~
      tup("label" -> Terminal.Identifier)).mapBoth {
      case (context, codeRange, label) => {
        val labelName = label.node.name
        val labelNode = LabelNode(Hashing.uuid, codeRange, labelName)
        (context.pushLabel(labelName, labelNode), labelNode)
      }
    } ~ tup("body" -> Statements.Statement)
  } mapBoth {
    case (context, codeRange, (labelNode, body)) => {
      val labelEdges = body.allNodes.map { node =>
        CreateEdge(labelNode, AnyNode(node), ESPrimaEdgeType.LabelContains).edge
      }
      val labelExp = ExpressionWrapper(
        labelNode,
        codeRange,
        body.allExpressions,
        Nil,
        labelEdges)

      (context.popLabel, StatementWrapper(
        codeRange,
        labelExp :: Nil,
        Nil))
    }
  }

  private def applyLabel[T <: ESPrimaNodeBuilder](context: ESPrimaContext, node: T, label: Option[String])(implicit validEdge: ValidEdge[T, LabelNode, ESPrimaEdgeType.BreakLabel.type]) = {
    val maybeLabelEdge = withDefined(label) { ll =>
      context.findLabel(ll).map { labelNode =>
        CreateEdge(node, labelNode, ESPrimaEdgeType.BreakLabel).edge
      }
    }

    val breakExp = ExpressionWrapper(
      node,
      node.range,
      Nil,
      Nil,
      maybeLabelEdge.toList)

    StatementWrapper(
      node.range,
      breakExp :: Nil,
      Nil)
  }

  val BreakStatement = {
    node("BreakStatement") ~
      tup("label" -> opt(Terminal.Identifier))
  } mapExtraction {
    case (context, codeRange, label) => {
      val breakNode = BreakNode(Hashing.uuid, codeRange, label.map(_.node.name))
      applyLabel(context, breakNode, label.map(_.node.name))
    }
  }

  val ContinueStatement = {
    node("ContinueStatement") ~ 
      tup("label" -> opt(Terminal.Identifier))
  } mapExtraction {
    case (context, codeRange, label) => {
      val continueNode = ContinueNode(Hashing.uuid, codeRange, label.map(_.node.name))
      applyLabel(context, continueNode, label.map(_.node.name))
    }
  }

  def ReturnStatement = {
    node("ReturnStatement") ~
      tup("argument" -> opt(Expressions.expression))
  } mapExtraction {
    case (context, codeRange, argument) => {
      val returnNode = ReturnNode(Hashing.uuid, codeRange)
      val maybeEdge = withDefined(argument) { arg =>
        CreateEdge(returnNode, AnyNode(arg.node), ESPrimaEdgeType.Return).edge :: arg.allNodes.map { node =>
          CreateEdge(returnNode, AnyNode(node), ESPrimaEdgeType.ReturnContains).edge
        }
      }

      val returnExp = ExpressionWrapper(
        returnNode,
        codeRange,
        argument.toList,
        Nil,
        maybeEdge)

      StatementWrapper(
        codeRange,
        returnExp :: Nil,
        Nil)
    }
  }

  sealed case class IfBlocksWrapper(
    blocks:   List[ExpressionWrapper[IfBlockNode]],
    children: List[StatementWrapper])

  private def IfExpression: Extractor[ESPrimaContext, IfBlocksWrapper] = {
    node("IfStatement") ~
      tup("test" -> Expressions.expression) ~
      tup("consequent" -> Statements.Statement) ~
      tup("alternate" -> opt(IfExpression or Statements.Statement))
  } mapExtraction {
    case (context, codeRange, ((test, consequent), alternate)) => {
      val ifBlockNode = IfBlockNode(Hashing.uuid, test.codeRange.span(consequent.codeRange)) // TODO: want to fix?

      val testEdge = CreateEdge(ifBlockNode, AnyNode(test.node), ESPrimaEdgeType.IfTest).edge
      val testContainsEdges = test.allNodes.map { node =>
        CreateEdge(ifBlockNode, AnyNode(node), ESPrimaEdgeType.IfTestContains).edge
      }
      val consequentEdges = consequent.allNodes.map { node =>
        CreateEdge(ifBlockNode, AnyNode(node), ESPrimaEdgeType.IfContains).edge
      }

      val alternateExpression = withDefined(alternate) {
        case Left(ifExp) => {
          Option(ifExp)
        }
        case Right(stmt) => {
          // create an if block
          val elseBlock = IfBlockNode(Hashing.uuid, stmt.codeRange)
          val elseEdges = stmt.allNodes.map { node =>
            CreateEdge(elseBlock, AnyNode(node), ESPrimaEdgeType.IfContains).edge
          }
          val elseExp = ExpressionWrapper(
            elseBlock,
            stmt.codeRange,
            Nil,
            Nil,
            elseEdges)
          Option(IfBlocksWrapper(elseExp :: Nil, stmt :: Nil))
        }
      }

      val alternateBlocks = withDefined(alternateExpression) { exp =>
        exp.blocks
      }

      val alternateChildren = withDefined(alternateExpression) { exp =>
        exp.children
      }

      val exp = ExpressionWrapper(
        ifBlockNode,
        codeRange,
        test :: Nil,
        Nil,
        testEdge :: (testContainsEdges ++ consequentEdges))

      IfBlocksWrapper(
        exp :: alternateBlocks,
        consequent :: alternateChildren)
    }
  }

  def IfStatement = {
    IfExpression.mapExtraction {
      case (context, codeRange, expWrapper) => {
        val ifNode = IfNode(Hashing.uuid, codeRange)

        val edges = expWrapper.blocks.map { b =>
          CreateEdge(ifNode, b.node, ESPrimaEdgeType.IfBlock).edge
        }

        val exp = ExpressionWrapper(
          ifNode,
          codeRange,
          Nil,
          Nil,
          edges)

        StatementWrapper(
          codeRange,
          exp :: expWrapper.blocks,
          expWrapper.children)
      }
    }
  }

  private def SwitchCase = {
    node("SwitchCase") ~
      tup("test" -> opt(Expressions.expression)) ~
      tup("consequent" -> sequence(Statements.Statement))
  } mapExtraction {
    case (context, codeRange, (test, consequent)) => {
      val block = SwitchBlockNode(Hashing.uuid, codeRange)

      val maybeTestEdge = withDefined(test) { t =>
        CreateEdge(block, AnyNode(t.node), ESPrimaEdgeType.SwitchTest).edge :: Nil 
      }

      val consequentEdges = consequent.flatMap(_.allNodes).map { node =>
        CreateEdge(block, AnyNode(node), ESPrimaEdgeType.SwitchContains).edge
      }

      ExpressionWrapper(
        block,
        codeRange,
        test.toList ++ consequent.flatMap(_.allExpressions),
        Nil,
        maybeTestEdge ++ consequentEdges)
    }
  }

  def SwitchStatement = {
    node("SwitchStatement") ~
      tup("discriminant" -> Expressions.expression) ~
      tup("cases" -> sequence(SwitchCase)) // this is a bullshit aspect of switch statements... fall-through
  } mapExtraction {
    case (context, codeRange, (discriminant, cases)) => {
      val switchNode = SwitchNode(Hashing.uuid, codeRange)

      val discriminantEdge = CreateEdge(switchNode, AnyNode(discriminant.node), ESPrimaEdgeType.SwitchDiscriminant).edge
      val caseEdges = cases.map { c => 
        CreateEdge(switchNode, c.node, ESPrimaEdgeType.SwitchBlock).edge
      }
      val exp = ExpressionWrapper(
        switchNode,
        codeRange,
        discriminant :: Nil,
        Nil,
        discriminantEdge :: caseEdges,
      )

      StatementWrapper(
        codeRange,
        exp :: cases,
        Nil
      )
    }
  }

  def WithStatement = {
    node("WithStatement") ~
    tup("object" -> Expressions.expression) ~ 
    tup("body" -> Statements.Statement) 
  } mapExtraction {
    case (context, codeRange, (obj, body)) => {
      val withNode = WithNode(Hashing.uuid, codeRange)

      val expEdge = CreateEdge(withNode, AnyNode(obj.node), ESPrimaEdgeType.With).edge
      val blockEdges = body.allNodes.map { node => 
        CreateEdge(withNode, AnyNode(node), ESPrimaEdgeType.WithContains).edge
      }

      val exp = ExpressionWrapper(
        withNode,
        codeRange,
        obj :: Nil,
        Nil,
        expEdge :: blockEdges
      )

      StatementWrapper(
        codeRange,
        exp :: Nil,
        body :: Nil
      )
    }
  }

  def statements = {
    ReturnStatement | BreakStatement | LabeledStatement | IfStatement | SwitchStatement | ContinueStatement | WithStatement
  }
}
