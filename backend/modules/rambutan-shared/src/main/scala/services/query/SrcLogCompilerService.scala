package services

import models.query._
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }
import silvousplay.imports._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.ws._
import play.api.libs.json._

@Singleton
class SrcLogCompilerService @Inject() (
  configuration:        play.api.Configuration,
  elasticSearchService: ElasticSearchService)(implicit mat: akka.stream.Materializer, ec: ExecutionContext) {

  val SrcLogLimit = 10

  private def spliceAdditionalNodeTraversals(tree: List[DirectedSrcLogEdge], query: SrcLogQuery) = {
    val nodesToCheck = {
      query.allNodes.groupBy(_.variable) flatMap {
        case (k, vs) => {
          vs match {
            case Nil      => None
            case a :: Nil => Some(k -> a)
            case a :: more => {
              val mostExact = more.foldLeft(a) {
                case (curr, next) => {
                  if (curr.predicate =/= next.predicate) {
                    throw new Exception(s"multiple constraints for node ${k} ${curr.predicate} ${next.predicate}")
                  } else {
                    if (next.condition.isDefined && curr.condition.isEmpty) {
                      next
                    } else {
                      curr
                    }
                  }
                }
              }
              Some(k -> mostExact)
            }
          }
        }
      }
    }

    // This adds an extra node check to the edge
    tree.map { directedEdge =>
      // TODO: this is bad. we need to genericize
      nodesToCheck.get(directedEdge.to) match {
        case _ if directedEdge.forceForwardDirection => {
          // don't traverse this way
          // for diffs, don't traverse because
          directedEdge
        }
        case Some(toNode) if Option(toNode.predicate) =/= directedEdge.intoImplicit || toNode.condition.isDefined => {
          // filter down in case edge is not specific enough
          // the implicit name / index condition should be handled by the edge search
          directedEdge.copy(nodeCheck = Some(toNode))
        }
        case any if directedEdge.mustNodeTraverse => {
          // for references we must traverse
          // TODO: need to make universal
          val toNode = any.getOrElse(NodeClause(JavascriptNodePredicate.NotIdentifierRef, "?", None)) // variable name ignored
          directedEdge.copy(nodeCheck = Some(toNode))
        }
        case _ => directedEdge
      }
    }
  }

  private def buildRelationalQuery(root: NodeClause, tree: List[DirectedSrcLogEdge], query: SrcLogQuery) = {
    val withNodeCheck = spliceAdditionalNodeTraversals(tree, query)

    // calculate missing edges to fill as intersections
    val missing = query.edges.flatMap { e =>
      withFlag(tree.find(_.equiv(e)).isEmpty) {
        Option(e)
      }
    }

    val tups = missing.map { m =>
      val baseEdge = if (m.modifier.isDefined) {
        // this will never happen, but let's explicitly call it out
        // should never have multiple boolean edges into a node
        // if there are multiple edges, the single boolean edge will always be prioritized first
        throw new Exception("should never get a boolean edge as an intersection")
      } else if (m.predicate.shouldReverseMissing) {
        // generally the case
        DirectedSrcLogEdge.reverse(m)
      } else {
        DirectedSrcLogEdge.forward(m)
      }
      val intersectTo = Hashing.uuid()
      (baseEdge.copy(to = intersectTo), (baseEdge.to, intersectTo))
    }

    //
    val allEdges = withNodeCheck ++ tups.map(_._1)
    val intersections = tups.map(_._2)

    val selects = query.selected match {
      case Nil => query.vertexes.toList
      case s   => s
    }

    // We shouldn't really need this, right?
    // val having = (query.vertexes -- query.leftJoinVertexes).map { k =>
    //   k -> HavingFilter.IS_NOT_NULL
    // }.toMap

    RelationalQuery(
      RelationalSelect.Select(selects),
      KeyedQuery(
        root.variable,
        root.getQuery),
      allEdges.map(_.toTraceQuery),
      Map(), // having,
      Map(),
      intersections.map {
        case (a, b) => a :: b :: Nil
      },
      offset = None,
      limit = None,
      forceOrdering = None)
  }

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

  /**
   * Algo:
   * 1. Take all nodes that can traverse to every other node
   * 2. Pick node with smallest count
   */
  private def chooseRoots[TU](nodePenalties: Map[String, Int], query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]) = {
    val nodesToCheck = query.allNodes.flatMap { n =>
      nodePenalties.get(n.variable) map { penalty =>
        n -> penalty
      }
    }
    val base = nodesToCheck.map {
      case (node, penalty) => {
        val query = node.getQuery
        elasticSearchService.count(
          targeting.nodeIndexName,
          targeting.rootQuery(query.root)) map { resp =>
            (node, (resp \ "count").as[Long] * math.pow(2, penalty))
          }
      }
    }

    Future.sequence(base).map { res =>
      val bestRoot = res.minBy(_._2)._1
      (bestRoot, res)
    }
  }

  private def getPenalties[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]) = {
    val allNodes = query.allNodes

    val baseTraversableEdges = query.baseTraversableEdges

    val withFlips = baseTraversableEdges.flatMap {
      case e if e.predicate.singleDirection && e.reverse => {
        // inject typeHint here <<<
        val hintNode = allNodes.find(_.variable =?= e.from).map(_.predicate)
        val flipped = e.flip(hintNode)
        List(flipped, e)
      }
      case other => {
        other :: Nil
      }
    }

    findRoots(withFlips, query.vertexes) match {
      case Nil => {
        throw new Exception("invalid srclog. could not find valid path")
      }
      case roots => {
        val possibleRoots = roots.map {
          case (node, edges) => {
            val forwardContains = edges.count(e => !e.reverse && e.predicate.singleDirection)
            (node, edges, forwardContains)
          }
        }

        val penalties = query.root.flatMap { s =>
          possibleRoots.find(_._1 =?= s).map(r => Map(r._1 -> r._3))
        }.getOrElse {
          possibleRoots.map(r => r._1 -> r._3).toMap
        }

        (withFlips, penalties)
      }
    }
  }

  private def findRoots(edges: List[DirectedSrcLogEdge], allVertexes: Set[String]): List[(String, List[DirectedSrcLogEdge])] = {
    // for a representative item in components, verifyConnection
    allVertexes.toList.flatMap { v =>
      scala.util.Try(spanningTree(v, allVertexes, edges)).toOption.map(v -> _)
    }
  }

  /**
   *
   */
  private def optimizeQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Future[RelationalQuery] = {
    // select node
    val (edges, penalties) = getPenalties(query)
    for {
      (root, possibleRoots) <- chooseRoots(penalties, query)
      tree = spanningTree(root.variable, query.vertexes, edges)
      q = buildRelationalQuery(root, tree, query)
    } yield {
      q
    }
  }

  /**
   * Local stuff
   */
  def compileQueryMultiple[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Future[Map[String, RelationalQuery]] = {
    val extractedComponents = SrcLogOperations.extractComponents(query)

    for {
      optimized <- Future.sequence {
        extractedComponents.map {
          case (k, v) => {
            compileQuery(v).map(k -> _)
          }
        }
      }
    } yield {
      optimized.toMap
    }
  }

  def compileQuery[TU](query: SrcLogQuery)(implicit targeting: QueryTargeting[TU]): Future[RelationalQuery] = {
    // hard code for now as we're only dealing with Javascript
    optimizeQuery(query)
  }
}
