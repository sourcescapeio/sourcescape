package models.graph.table

import models.graph._
import play.api.libs.json._

case class TableNode(
  name: String) extends GenericNodeBuilder(GenericGraphNodeType.Table)

case class RowNode(
  name: String) extends GenericNodeBuilder(GenericGraphNodeType.Row)

case class CellNode(
  name: String) extends GenericNodeBuilder(GenericGraphNodeType.Cell)

/**
 * Specific impl
 */
object CellWriter {
  def write(column: String, item: JsValue) = {
    ExpressionWrapper(
      CellNode("test"),
      Nil,
      Nil,
      Nil)
  }
}

object RowWriter {
  def write(item: Map[String, JsValue]) = {
    val rowNode = RowNode("test")
    val cells = item.map {
      case (k, v) => CellWriter.write(k, v)
    }.toList

    val cellEdges = cells.map { cell =>
      CreateEdge(rowNode, cell.node, GenericEdgeType.RowCell).edge()
    }

    ExpressionWrapper(
      rowNode,
      cells,
      Nil,
      cellEdges)
  }
}

// case class TableContext()
// case class RowContext()

// object TableWriter {
//   case object Table extends GenericNodeWriter
// }

// object TableWriter {
//   def write(item: JsValue) = {
//     println(item)
//   }
// }
