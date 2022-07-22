package models.query

import models.query.display._
import silvousplay.imports._

case class BuilderTree(
  node:     BuilderNode,
  children: List[BuilderEdge]) {

  def print(indents: Int): Unit = {
    println((" " * indents) + node.print + "]")
    children.foreach(_.print(indents + 2))
  }

  def allIds: List[String] = {
    node.id :: children.flatMap(_.allIds)
  }

  // NOTE: mirroring this method so if we every change the impl for allIds,
  // we keep breathFirstSearch
  def breadthFirstSearch: List[String] = {
    node.id :: children.flatMap(_.tree.allIds)
  }

  def allIdsWithoutReferences: List[String] = {
    withFlag(node.nodeType =/= BuilderNodeType.Reference) {
      node.id :: Nil
    } ++ children.flatMap(_.tree.allIdsWithoutReferences)
  }

  def allReferences: List[String] = {
    withFlag((node.nodeType =?= BuilderNodeType.Reference && node.alias.isEmpty)) {
      node.id :: Nil
    } ++ children.flatMap(_.tree.allReferences)
  }

  def allIdsTo(terminus: String, prev: List[String]): List[String] = {
    if (node.id =?= terminus) {
      prev
    } else {
      children.flatMap { c =>
        c.tree.allIdsTo(terminus, prev :+ node.id) match {
          case Nil   => None
          case other => Some(other)
        }
      }.headOption.getOrElse(Nil)
    }
  }

  def display(parentId: String): DisplayStruct = {
    val childrenDisplay = {
      children.map(_.display(node.id)).groupBy(_._1).map {
        case (k, vs) => k -> vs.map(_._2)
      }
    }

    node.display(parentId, childrenDisplay)
  }

  def followReferences: BuilderTree = {
    this.copy(children = children.flatMap(_.followReferences))
  }

  def forceReferences(referenceSet: Set[String]): BuilderTree = {
    this.copy(
      node = node.forceReferences(referenceSet),
      children = children.map(_.forceReferences(referenceSet)))
  }

  def followTo(to: String): Option[BuilderTree] = {
    if (node.id =?= to) {
      Some(this)
    } else {
      children.flatMap { e =>
        e.tree.followTo(to)
      }.headOption
    }
  }

  def clipTo(to: String): Option[(Boolean, BuilderTree)] = {
    if (node.id =?= to) {
      Some(display("").alias.isDefined, this.followReferences)
    } else {
      children.flatMap { e =>
        e.tree.clipTo(to).map {
          case (true, newTree) => {
            // already clipped
            (true, newTree)
          }
          case (false, newTree) => {
            val hasAlias = display("").alias.isDefined
            val newEdge = e.copy(tree = newTree)

            (hasAlias, this.copy(children = newEdge :: Nil))
          }
        }
      }.headOption
    }
  }

  def splice(edge: EdgeClause, tree: BuilderTree): BuilderTree = {
    if (node.id =?= edge.from) {
      // add edge
      val newEdge = BuilderEdge.fromClause(edge, tree)
      this.copy(children = newEdge :: children)
    } else {
      this.copy(children = children.map(_.splice(edge, tree)))
    }
  }
}

object BuilderTree {
  // assumption: this is a connected query
  private def findRoots(query: SrcLogQuery) = {
    val incomingCounts = query.edges.groupBy(_.to).map {
      case (k, vs) => k -> vs.length
    }

    val allVertexes = query.vertexes

    allVertexes.filter { v =>
      incomingCounts.getOrElse(v, 0) =?= 0
    }
  }

  private def forcedRoots(query: SrcLogQuery) = {
    // Any Node
    val requireDependencies = query.edges.flatMap {
      case e if e.predicate =?= UniversalEdgePredicate.RequireDependency => Some(e.to)
      case _ => None
    }.toSet
    query.allNodes.filter(_.predicate.forceRoot).map(_.variable).toSet ++ requireDependencies
  }

  private def findClipEdges(edges: List[EdgeClause]) = {
    val allClip = edges.groupBy(_.to).toList.flatMap {
      case (k, vs) if vs.length > 1 => {
        // vs.sortBy priority
        val sortedVs = vs.map { v =>
          val p = BuilderEdgeType.fromPredicate(v.predicate).priority
          p -> v
        }.sortBy(_._1.priority)

        sortedVs.drop(1)
      }
      case _ => Nil
    }

    // should error out if we clip any 1s?
    val droppedP1 = allClip.exists {
      case (p, _) => p =?= BuilderEdgePriority.Required
    }
    if (droppedP1) {
      throw new Exception("Dropped a priority 1 edge")
    }

    allClip.map(_._2).toList
  }

  private def fillRoot(
    root:      String,
    query:     SrcLogCodeQuery,
    clipEdges: Set[(String, String)]) = {

    val nodeMap = query.allNodes.map { n =>
      n.variable -> n
    }.toMap

    val edgeMap = query.edges.flatMap {
      case e if clipEdges.contains(e.from, e.to) => None
      case e                                     => Some((e.from, e))
    }.groupBy(_._1).map {
      case (k, vs) => k -> vs.map(_._2)
    }

    // references
    // (from, to)
    // (getTree at to)
    def fillVertex(vertex: String): BuilderTree = {
      val baseNode = nodeMap.get(vertex).map(BuilderNode.fromClause).getOrElse {
        BuilderNode(BuilderNodeType.Any, vertex, None, None, None, false)
      }

      // apply alias to nodes
      val node = query.aliases.get(vertex).foldLeft(baseNode) {
        case (acc, next) => acc.copy(alias = Some(next))
      }

      val edges = edgeMap.getOrElse(vertex, Nil).map { edge =>
        BuilderEdge.fromClause(edge, fillVertex(edge.to))
      }

      BuilderTree(
        node,
        edges)
    }

    // First: build a spanning tree
    val tree = fillVertex(root)

    tree
  }

  private def spliceReferences(tree: BuilderTree, references: List[EdgeClause], allTrees: List[BuilderTree]) = {
    references.foldLeft(tree) {
      case (acc, r) => {
        val aliasInArg = {
          // We don't replace args as references
          val v1 = allTrees.find(_.followTo(r.to).isDefined).flatMap(_.followTo(r.to))

          v1.flatMap { v =>
            // TODO: genericize
            withFlag(v.node.nodeType =?= JavascriptBuilderNodeType.FunctionArg) {
              v.node.alias
            }
          }
        }

        val refTree = BuilderTree(
          BuilderNode(
            BuilderNodeType.Reference,
            r.to,
            None,
            None,
            aliasInArg,
            false),
          Nil)

        acc.splice(r, refTree)
      }
    }
  }

  // private def calculateExternalReferences()

  /**
   * Algo:
   * 1. clip edges
   * 2. for clipped edges, fill in tree with a reference
   * 3. force define container (D := ____) for references that we use
   */
  def buildFromQuery(query: SrcLogCodeQuery) = {
    // some nodes (esp. requires) are forcibly roots
    val forceRoots = forcedRoots(query)
    val roots = findRoots(query) ++ forceRoots

    val forceRootEdges = query.edges.filter(forceRoots contains _.to)
    val allClip = findClipEdges(query.edges) ++ forceRootEdges

    val clipEdges = allClip.map { v =>
      v.from -> v.to
    }.toSet

    val components = roots.toList map { root =>
      root -> fillRoot(root, query, clipEdges)
    }

    println("====BASE====")
    println(roots)
    components map {
      case (k, v) => v.print(0)
    }

    // ************
    // Calculate internal references (references that we clipped internal to a tree)
    val splicedComponents = components.map {
      case (root, tree) => {
        root -> spliceReferences(tree, allClip, components.map(_._2).toList)
      }
    }

    println("====SPLICED====")
    splicedComponents map {
      case (k, v) => v.print(0)
    }

    splicedComponents.map(_._2)
  }
}
