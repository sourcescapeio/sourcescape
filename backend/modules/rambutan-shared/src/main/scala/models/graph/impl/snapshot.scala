package models.graph.snapshot

import models.graph._
import play.api.libs.json._
import models._
import silvousplay.imports._

case class SchemaNode(schema: Schema)
  extends GenericNodeBuilder(
    GenericGraphNodeType.Schema,
    List(
      GenericGraphProperty("schema_id", schema.id.toString())),
    idempotencyKey = Some("schema::" + schema.id.toString()))

case class SchemaColumnNode(schema: Schema, column: String, name: Option[String], index: Int)
  extends GenericNodeBuilder(
    GenericGraphNodeType.SchemaColumn,
    List(
      GenericGraphProperty("column", column) :: Nil,
      GenericGraphProperty("index", index.toString()) :: Nil,
      withDefined(name) { n =>
        GenericGraphProperty("name", n) :: Nil
      }).flatten,
    idempotencyKey = Some("column::" + schema.id.toString() + "::" + column)) {

  val columnProps = List(
    Option(GenericGraphProperty("column", column)),
    name.map(n => GenericGraphProperty("column_name", n))).flatten
}

case class SnapshotNode(schema: Schema, indexId: Int)
  extends GenericNodeBuilder(
    GenericGraphNodeType.Snapshot,
    List(
      GenericGraphProperty("schema_id", schema.id.toString()),
      GenericGraphProperty("index_id", indexId.toString())),
    payload = Json.obj(
      "schema_id" -> schema.id,
      "index_id" -> indexId))

case class SnapshotRowNode(schemaId: Int, indexId: Int, rowKey: String)
  extends GenericNodeBuilder(
    GenericGraphNodeType.SnapshotRow,
    List(
      GenericGraphProperty("schema_id", schemaId.toString()),
      GenericGraphProperty("index_id", indexId.toString()),
      GenericGraphProperty("row_key", rowKey)),
    payload = Json.obj(
      "schema_id" -> schemaId,
      "index_id" -> indexId,
      "row_key" -> rowKey),
    idempotencyKey = Some(s"row::${schemaId}::${indexId}::${rowKey}"))

//, nearby: String, extracted: String
case class SnapshotCellNode(checksum: String, column: SchemaColumnNode)
  extends GenericNodeBuilder(
    GenericGraphNodeType.SnapshotCell,
    List(
      GenericGraphProperty("checksum", checksum) :: Nil,
      column.columnProps).flatten)

case class SnapshotCellDataNode(data: JsValue)
  extends GenericNodeBuilder(
    GenericGraphNodeType.SnapshotCellData,
    payload = data)

case class AnnotationNode(annotation: Annotation)
  extends GenericNodeBuilder(
    GenericGraphNodeType.Annotation,
    List(
      GenericGraphProperty("column_id", annotation.colId.toString()),
      GenericGraphProperty("index_id", annotation.indexId.toString()),
      GenericGraphProperty("row_key", annotation.rowKey)),
    payload = annotation.payload,
    idempotencyKey = Some(s"annotation::${annotation.id}"))

// context

object SnapshotWriter {

  private def materializeCell(data: JsValue, column: SchemaColumnNode) = {
    val checksum = (data \ "checksum").as[String]
    val cellNode = SnapshotCellNode(checksum, column)
    val cellDataNode = SnapshotCellDataNode(data)

    val columnEdge = CreateEdge(column, cellNode, GenericEdgeType.SnapshotColumnCell).edge()
    val dataEdge = CreateEdge(cellNode, cellDataNode, GenericEdgeType.SnapshotCellData).edge()

    ExpressionWrapper(
      cellNode,
      Nil,
      cellDataNode :: Nil,
      columnEdge :: dataEdge :: Nil)
  }

  def materializeSnapshot(
    schema: Schema,
    index:  RepoSHAIndex) = {
    val indexId = index.id
    val schemaNode = SchemaNode(schema)
    val snapshotNode = SnapshotNode(schema, indexId)

    val schemaColumns = schema.selected.zipWithIndex.map {
      case (col, idx) => {
        val name = schema.fieldAliasesMap.get(col)
        println(schema.fieldAliasesMap)
        SchemaColumnNode(schema, col, name, idx)
      }
    }

    val colExpressions = schemaColumns.map { colNode =>
      val edge = CreateEdge(schemaNode, colNode, GenericEdgeType.SchemaColumn).edge(
        "name" -> colNode.name.getOrElse(""))
      ExpressionWrapper(
        colNode,
        Nil,
        Nil,
        edge :: Nil)
    }

    val schemaSnapshot = CreateEdge(schemaNode, snapshotNode, GenericEdgeType.SchemaSnapshot).edge(
      "index_id" -> indexId.toString(),
      "schema_id" -> schema.id.toString())

    val codeNode = git.CodeIndex(index.repoId, index.repoName, index.sha, index.id)

    val snapshotCodeIndex = CreateEdge(snapshotNode, codeNode, GenericEdgeType.SnapshotCodeIndex).edge()

    ExpressionWrapper(
      snapshotNode,
      colExpressions,
      schemaNode :: Nil,
      schemaSnapshot :: snapshotCodeIndex :: Nil) -> schemaColumns
  }

  def materializeRow(data: Map[String, JsValue], snapshot: SnapshotNode, columns: List[SchemaColumnNode]) = {
    val columnMap = columns.map { c =>
      c.column -> c
    }.toMap

    val cells = data.map {
      case (k, v) => {
        materializeCell(
          (v \ "terminus" \ "node").as[JsValue],
          columnMap.getOrElse(k, throw new Exception("invalid cell")))
      } // add column
    }.toList

    val rowKey = snapshot.schema.sequence match {
      case head :: rest => {
        val rootKey = {
          val node = data.get(head).get \ "terminus" \ "node"
          val defaultedName = (node \ "name").asOpt[String].getOrElse((node \ "checksum").as[String])
          Keyable.encode {
            (node \ "path").as[String] + ":" + defaultedName
          }
        }

        val restKeys = rest.map { r =>
          val node = data.get(r).get \ "terminus" \ "node"
          val defaultedName = (node \ "name").asOpt[String].getOrElse((node \ "checksum").as[String])

          Keyable.encode(defaultedName)
        }

        s"${rootKey}//${restKeys.mkString("//")}"
      }
      case _ => throw new Exception("invalid sequence for schema")
    }

    val rowNode = SnapshotRowNode(snapshot.schema.id, snapshot.indexId, rowKey)

    val cellEdges = cells.map { cell =>
      val columnProps = cell.node.column.columnProps.map { prop =>
        prop.key -> prop.value
      }
      CreateEdge(rowNode, cell.node, GenericEdgeType.SnapshotRowCell).edge(
        columnProps: _*)
    }

    val snapshotEdge = CreateEdge(snapshot, rowNode, GenericEdgeType.SnapshotRow).edge()

    ExpressionWrapper(
      rowNode,
      cells,
      Nil,
      snapshotEdge :: cellEdges)
  }
}

object AnnotationWriter {
  def materializeAnnotation(column: AnnotationColumn, annotation: Annotation) = {
    val annotationNode = AnnotationNode(annotation)
    val rowNode = SnapshotRowNode(column.schemaId, annotation.indexId, annotation.rowKey)

    val edge = CreateEdge(rowNode, annotationNode, GenericEdgeType.SnapshotRowAnnotation).edge(
      "column_id" -> column.id.toString())

    ExpressionWrapper(
      annotationNode,
      Nil,
      Nil,
      edge :: Nil)
  }
}
