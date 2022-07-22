package services

import models.query._
import models.index.GraphNode
import models.graph.GenericGraphNode

trait Groupable[T] {

  def name(in: T): String
  def id(in: T): String

  def displayKeys(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[T]]): List[(String, String)]

  def diffKey(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[T]]): String
}

object Groupable {

  implicit val graphNode = new Groupable[(String, GraphNode)] {
    def name(in: (String, GraphNode)): String = in._2.name.getOrElse(in._2.id)
    def id(in: (String, GraphNode)): String = in._2.id

    def displayKeys(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[(String, GraphNode)]]): List[(String, String)] = {
      val grip = groupedCount.grip

      val initial = groupedCount.grouping match {
        case GroupingType.None => {
          Nil
        }
        case GroupingType.Repo => {
          in.get(grip).map(i => "repo" -> i.terminusId._2.repo).toList
        }
        case GroupingType.RepoFile => {
          in.get(grip).toList.flatMap { i =>
            ("repo", i.terminusId._2.repo) :: ("file" -> i.terminusId._2.path) :: Nil
          }
        }
      }

      val keys = groupedCount.columns.flatMap { i =>
        in.get(i).map(ii => i -> ii.terminusId._2.name.getOrElse("NULL")) // ewww
      }

      (initial ++ keys)
    }

    def diffKey(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[(String, GraphNode)]]) = {
      val grip = groupedCount.grip

      val initial = groupedCount.grouping match {
        case GroupingType.None     => ""
        case GroupingType.Repo     => in.get(grip).map(_.terminusId._2.repo).getOrElse("")
        case GroupingType.RepoFile => in.get(grip).map(i => i.terminusId._2.repo + "|" + i.terminusId._2.path).getOrElse("")
      }

      val keys = groupedCount.columns.map { i =>
        in.get(i).flatMap(_.terminusId._2.name).getOrElse("NULL")
      }.mkString("|")

      s"${initial}|${keys}"
    }
  }

  implicit val genericGraphNode = new Groupable[GenericGraphNode] {
    def name(in: GenericGraphNode): String = in.names.headOption.getOrElse(in.id)
    def id(in: GenericGraphNode): String = in.id

    def displayKeys(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[GenericGraphNode]]): List[(String, String)] = {
      throw new Exception("not implemented")
    }

    def diffKey(groupedCount: RelationalSelect.GroupedCount, in: Map[String, GraphTrace[GenericGraphNode]]) = {
      throw new Exception("not implemented")
    }

  }
}
