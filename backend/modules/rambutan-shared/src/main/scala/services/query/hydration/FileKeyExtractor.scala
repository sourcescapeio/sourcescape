package services

import models.index.GraphNode
import models.graph.GenericGraphNode
import models.query.FileKey

trait FileKeyExtractor[T] {
  def extract(in: T): Option[FileKey]
}

object FileKeyExtractor {
  implicit val graphNode = new FileKeyExtractor[(String, GraphNode)] {
    def extract(in: (String, GraphNode)) = Some(in._2.fileKey)
  }

  implicit val genericGraphNode = new FileKeyExtractor[GenericGraphNode] {
    def extract(in: GenericGraphNode) = None
  }
}
