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
import akka.stream.scaladsl.Source
import models.Sinks

@Singleton
class SrcLogCompilerService @Inject() (
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val SrcLogLimit = 10

  def compileQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Future[RelationalQuery] = {
    optimizeQuery(query)
  }

  /**
   * Core logic
   */
  private def optimizeQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Future[RelationalQuery] = {
    // select node
    val traversableEdges = calculateAllTraversableEdges(query)
    val allSpanningTrees = calculateAllSpanningTrees(traversableEdges, query.vertexes)
    val allRoots = allSpanningTrees.map(_._1).toSet

    for {
      nodeCosts <- getNodeCosts(query.allNodes.filter(n => allRoots.contains(n.variable)))
      withCosts = allSpanningTrees.map {
        case (rootKey, (edges, penalty)) => {
          val nodeCost = nodeCosts.getOrElse(rootKey, throw new Exception("invalid error"))
          (rootKey, edges, penalty * nodeCost)
        }
      }
      (rootKey, optimalTree, _) = withCosts.minBy(_._3)
      rootNode = query.nodeMap.getOrElse(rootKey, throw new Exception(s"Invalid root ${rootKey}"))
    } yield {
      buildRelationalQuery(rootNode, optimalTree, query)
    }
    // } yield {
    //   q
    // }
  }

  /**
   * 1. For each EdgeClause, emit all directed possibilities based on settings
   */
  private def calculateAllTraversableEdges(query: SrcLogQuery) = {
    val nodeMap = query.nodeMap

    query.edges.flatMap {
      case e @ EdgeClause(p, from, to, c, Some(_)) => {
        // We have a boolean modifier here so we need to left join
        DirectedSrcLogEdge.forward(e, nodeMap) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, _) if p.forceForward => {
        DirectedSrcLogEdge.forward(e, nodeMap) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) if p.forceReverse => {
        DirectedSrcLogEdge.reverse(e, nodeMap) :: Nil
      }
      case e @ EdgeClause(p, from, to, c, None) => {
        DirectedSrcLogEdge.forward(e, nodeMap) :: DirectedSrcLogEdge.reverse(e, nodeMap) :: Nil
      }
    }
  }

  /**
   * 2. Calculate all spanning trees given directed edges, with edge cost
   */
  private def calculateAllSpanningTrees(edges: List[DirectedSrcLogEdge], allVertexes: Set[String]): Map[String, (List[DirectedSrcLogEdge], Int)] = {
    // for a representative item in components, verifyConnection
    (allVertexes.toList.flatMap { v =>
      scala.util.Try {
        spanningTree(v, allVertexes, edges)
      }.toOption.map(v -> _)
    } match {
      case Nil      => throw new Exception("invalid srclog. could not find valid path")
      case nonEmpty => nonEmpty
    }).toMap
  }

  /**
   * Algo (Greedy DFS):
   * 1. Start with root node as frontier
   * 2. Expand frontier by checking all available edges from frontier to new vertexes
   * 3. If already at all vertexes, then terminate
   * 4. If not, add one edge with lowest cost to list, add new vertex to frontier
   */
  private def spanningTree(root: String, allVertexes: Set[String], traversableEdges: List[DirectedSrcLogEdge]): (List[DirectedSrcLogEdge], Int) = {
    val traversableEdgeMap = traversableEdges.groupBy(_.from)

    def traverse(frontier: Set[String], edges: List[DirectedSrcLogEdge], penalty: Int): (List[DirectedSrcLogEdge], Int) = {
      if (frontier.equals(allVertexes)) {
        (edges, penalty)
      } else {
        val possibleExpansion = frontier.toList.flatMap(f => traversableEdgeMap.getOrElse(f, Nil)).filterNot { e =>
          frontier.contains(e.to)
        }

        val chosenEdge = possibleExpansion.minBy(_.edgePenalty)

        traverse(frontier + chosenEdge.to, edges :+ chosenEdge, penalty * chosenEdge.edgePenalty) // NOTE: must be in order
      }
    }

    traverse(Set(root), Nil, penalty = 1)
  }

  /**
   * 3. Get node root count to calculate true cost
   */
  private def getNodeCosts[TU](nodes: List[NodeClause])(implicit targeting: QueryTargeting[TU]): Future[Map[String, Long]] = {
    Source(nodes).mapAsync(4) { node =>
      val query = node.getQuery
      elasticSearchService.count(
        targeting.nodeIndexName,
        targeting.rootQuery(query.root)) map { resp =>
          node.variable -> (resp \ "count").as[Long]
        }
    }.runWith(Sinks.ListAccum).map(_.toMap)
  }

  /**
   * 4. Build relational query
   */
  private def buildRelationalQuery(root: NodeClause, tree: List[DirectedSrcLogEdge], query: SrcLogQuery): RelationalQuery = {
    // edges that are not included in the spanning tree are filled in as intersections
    // TODO: this is not accurately accounted for in cost calculation
    val missingTuples = fillMissingEdges(tree, query)
    val missingEdges = missingTuples.map(_._1)
    val intersections = missingTuples.map(_._2)

    // we need to do a first pass to see what follows are passed forward

    val allEdges = (tree ++ missingEdges)

    val traceQueries = allEdges.map { edge =>
      calculateTraceQuery(edge)
    }

    val selects = query.selected match {
      case Nil => query.vertexes.toList
      case s   => s
    }

    RelationalQuery(
      RelationalSelect.Select(selects),
      KeyedQuery(
        root.variable,
        root.getQuery),
      traceQueries,
      Map(), // having,
      Map(), // havingOr
      intersections.map {
        case (a, b) => a :: b :: Nil
      },
      offset = None,
      limit = None,
      forceOrdering = None)
  }

  /**
   * Relational query helpers
   */
  private def fillMissingEdges(tree: List[DirectedSrcLogEdge], query: SrcLogQuery) = {
    // calculate missing edges to fill as intersections
    val missing = query.edges.flatMap { e =>
      withFlag(tree.find(_.equiv(e)).isEmpty) {
        Option(e)
      }
    }

    missing.map { m =>
      val baseEdge = if (m.modifier.isDefined) {
        // this will never happen, but let's explicitly call it out
        // should never have multiple boolean edges into a node
        // if there are multiple edges, the single boolean edge will always be prioritized first
        throw new Exception("should never get a boolean edge as an intersection")
      } else if (m.predicate.shouldReverseMissing) {
        // generally the case
        DirectedSrcLogEdge.reverse(m, Map.empty[String, NodeClause])
      } else {
        DirectedSrcLogEdge.forward(m, Map.empty[String, NodeClause])
      }

      val intersectTo = Hashing.uuid()
      val mappedEdge = baseEdge.copy(
        to = intersectTo,
        nodeCheck = query.nodeMap.get(baseEdge.to))
      (mappedEdge, (baseEdge.to, intersectTo))
    }
  }

  def calculateTraceQuery(directedEdge: DirectedSrcLogEdge) = {
    // these conditions should never happen
    if (directedEdge.booleanModifier.isDefined && directedEdge.reverse) {
      throw new Exception("INVALID: boolean edges must be forward facing")
    }

    // Need to create a Map of Map[Node -> reversed edges into it]
    // edgeTraverse looks at this map
    // nodeTraverse looks at self
    val traverses = directedEdge.edgeTraverse ++ directedEdge.nodeTraverse

    val leftJoin = directedEdge.booleanModifier.isDefined

    KeyedQuery(
      directedEdge.to,
      TraceQuery(
        FromRoot(directedEdge.from, leftJoin = leftJoin),
        traverses))
  }
}
