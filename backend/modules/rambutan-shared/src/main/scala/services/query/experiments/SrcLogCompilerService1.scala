package services.q1

import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._
import services.ElasticSearchService

@Singleton
class SrcLogCompilerService @Inject() (
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val SrcLogLimit = 10

  //: Future[RelationalQuery]
  def compileQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]) = {
    optimizeQuery(query)
  }

  /**
   * Core logic
   */
  // : Future[RelationalQuery]
  private def optimizeQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]) = Future.successful {
    // select node
    val traversableEdges = calculateAllTraversableEdges(query)
    traversableEdges.foreach(println)

    val penalties = calculatePenalties(traversableEdges, query)
    println(penalties)
    // println(penalties)
    // for {
    //   root <- chooseRoots(penalties, query.allNodes)
    //   tree = spanningTree(root.variable, query.vertexes, edges)
    //   q = buildRelationalQuery(root, tree, query)
    // } yield {
    //   q
    // }
  }

  /**
   * 1. For each EdgeClause, emit all directed possibilities based on settings
   */
  private def calculateAllTraversableEdges(query: SrcLogQuery) = {
    val nodeMap = query.allNodes.map { 
      case n => n.variable -> n
    }.toMap // unique by variable

    println(nodeMap)

    // should we inject node hints here? YES
    query.edges.flatMap {
      case e @ EdgeClause(p, from, to, c, Some(_)) => {
        // All booleans must be forward because of left join
        DirectedSrcLogEdge.forward(e) :: Nil
      }
      // TODO: clean this shit up?
      case e @ EdgeClause(p, from, to, c, _) if p.forceForward => {
        DirectedSrcLogEdge.forward(e) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) if p.forceReverse => {
        DirectedSrcLogEdge.reverse(e) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) => {
        DirectedSrcLogEdge.forward(e) :: DirectedSrcLogEdge.reverse(e) :: Nil
      }
    }
  }

  /**
   * 2. Calculate penalties for each possibility
   */
  private def calculatePenalties[TU](traversableEdges: List[DirectedSrcLogEdge], query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Map[String, Int] = {
    calculateAllSpanningTrees(traversableEdges, query.vertexes) match {
      case Nil => {
        throw new Exception("invalid srclog. could not find valid path")
      }
      case roots => {
        val possibleRoots = roots.map {
          case (node, edges) => {
            val forwardContains = edges.count(e => !e.reverse && e.predicate.singleDirection)
            // every forward contains counts as a penalty
            (node, edges, forwardContains)
          }
        }

        query.root.flatMap { s =>
          possibleRoots.find(_._1 =?= s).map(r => Map(r._1 -> r._3))
        }.getOrElse {
          possibleRoots.map(r => r._1 -> r._3).toMap
        }
      }
    }
  }

  /**
   * Algo:
   * 1. Take all nodes that can traverse to every other node
   * 2. Pick node with smallest count
   */
  private def calculateAllSpanningTrees(edges: List[DirectedSrcLogEdge], allVertexes: Set[String]): List[(String, List[DirectedSrcLogEdge])] = {
    // for a representative item in components, verifyConnection
    allVertexes.toList.flatMap { v =>
      scala.util.Try(spanningTree(v, allVertexes, edges)).toOption.map(v -> _)
    }
  }

  /**
   * Helpers
   */
  /**
   * Algo (DFS):
   * 1. Start with root node as frontier
   * 2. Expand frontier by checking all available edges from frontier to new vertexes
   * 3. If already at all vertexes, then terminate
   * 4. If not, add one edge with lowest cost to list, add new vertex to frontier
   */
  private def spanningTree(root: String, allVertexes: Set[String], traversableEdges: List[DirectedSrcLogEdge]): List[DirectedSrcLogEdge] = {
    val traversableEdgeMap = traversableEdges.groupBy(_.from)

    def traverse(frontier: Set[String], edges: List[DirectedSrcLogEdge]): List[DirectedSrcLogEdge] = {
      if (frontier.equals(allVertexes)) {
        edges
      } else {
        val possibleExpansion = frontier.toList.flatMap(f => traversableEdgeMap.getOrElse(f, Nil)).filterNot { e =>
          frontier.contains(e.to)
        }

        val chosenEdge = possibleExpansion.minBy(_.cost)

        traverse(frontier + chosenEdge.to, edges :+ chosenEdge) // NOTE: must be in order
      }
    }

    traverse(Set(root), Nil)
  }

}