package models.query

import models.query.display._
import play.api.libs.json._
import silvousplay.imports._
import pprint._

sealed trait GenericPayload {
  def ids: List[Int]
}

private case class EdgePayload(
  from: Either[Int, String], 
  to: Either[Int, String], 
  predicate: EdgePredicate,
  name: Option[String],
  index: Option[Int],
  alias: Option[String]
) extends GenericPayload {
  def ids = {
    from.left.toOption.toList ++ to.left.toOption.toList
  }

  def aliasMap(idMap: Map[Int, String]) = {
    alias.map { a => 
      val toId = to match {
        case Left(t) => idMap.getOrElse(t, throw new Exception("invalid from"))
        case Right(t) => t
      }

      toId -> a
    }
  }

  def toClause(idMap: Map[Int, String]) = {
    val fromId = from match {
      case Left(f) => idMap.getOrElse(f, throw new Exception("invalid from"))
      case Right(f) => f
    }

    val toId = to match {
      case Left(t) => idMap.getOrElse(t, throw new Exception("invalid from"))
      case Right(t) => t
    }

    val condition = (name, index) match {
      case (Some(n), _) => Some(NameCondition(n))
      case (None, Some(i)) => Some(IndexCondition(i))
      case _ => None
    }

    EdgeClause(
      predicate,
      from = fromId,
      to = toId,
      condition = condition,
      modifier = None)    
  }
}

private object EdgePayload {
  implicit val rr = new Reads[Either[Int, String]] {
    def reads(json: JsValue) = {
      json match {
        case JsNumber(v) => JsSuccess(Left(v.intValue))
        case JsString(v) => JsSuccess(Right(v))
        case _ => JsError(Seq())
      }
    }
  }

  implicit val reads = Json.reads[EdgePayload]
}

private case class NodePayload(id: Either[Int, String], predicate: NodePredicate, name: Option[String]) extends GenericPayload {
  def ids = id.left.toOption.toList

  def toClause(idMap: Map[Int, String]) = {
    val nextId = id match {
      case Left(i) => idMap.getOrElse(i, throw new Exception("invalid id"))
      case Right(i) => i
    }

    NodeClause(
      predicate,
      nextId,
      withDefined(name) { n =>
        Option(NameCondition(n))
      }
    )
  }
}

private object NodePayload {
  import EdgePayload._
  implicit val reads = Json.reads[NodePayload]
}

private case class DeletePayload(from: String, to: String)

private object DeletePayload {
  implicit val reads = Json.reads[DeletePayload]
}

private case class FullPayload(
  nodes: List[NodePayload],
  edges: List[EdgePayload],
  selected: Option[Either[Int, String]],
  replace: Option[Boolean]
) {

  def defaultedReplace = replace.getOrElse(false)

  def clauses(query: SrcLogCodeQuery) = {
    val idMap = (nodes.flatMap(_.ids) ++ edges.flatMap(_.ids)).distinct.map { id =>
      id -> query.incVertexId(id - 1)
    }.toMap

    (
      idMap,
      nodes.map(_.toClause(idMap)),
      edges.map(_.toClause(idMap)),
      edges.flatMap(_.aliasMap(idMap)).toMap
    )
  }
}

private object FullPayload {
  import EdgePayload._  
  implicit val reads = Json.reads[FullPayload]
}

case class BuilderState(
  query: SrcLogCodeQuery,
  selected: Option[String],
  traceSelected: Map[String, Boolean], // overrides
  trace: Boolean,
  update: Boolean
) {

  private def recalculateSelected = {
    selected match {
      case Some(s) if query.vertexes.contains(s) => {
        this
      }
      case _ => {
        // simple selection defaulting (just pick first)
        // this is really only applicable for adding in bulk
        this.copy(
          selected = query.vertexes.headOption
        )
      }
    }
  }

  private def selectPayload(nextQuery: SrcLogCodeQuery, payloadSelected: Option[String]) = {
    (this.selected, payloadSelected) match {
      case (None, _) => payloadSelected
      case (Some(i), Some(p)) => {
        // don't select new thing in same tree
        val nextTree = BuilderTree.buildFromQuery(nextQuery)
        nextTree.find(_.allIds.contains(i)) match {
          case Some(t) if t.allIds.contains(p) => Some(i)
          case Some(t) => Some(p)
          case None => Some(p)
        }
      }
      case _ => this.selected
    }
  }

  // remap contains edges
  private def calculateContainsRemap(edges: List[EdgeClause]) = {

    // external edges
    val querySet = query.allNodes.map(_.variable).toSet
    // get nodes that are linked back to existing query
    // (internal, external)
    val externalNodes = edges.flatMap { e => 
      List(
        withFlag(querySet.contains(e.to) && !querySet.contains(e.from))(Option(e.to)),
        withFlag(querySet.contains(e.from) && !querySet.contains(e.to))(Option(e.from))
      ).flatten
    }.toSet
    // find edge nodes that are contained in existing builder tree
    val trees = BuilderTree.buildFromQuery(query)
    val treeList = trees.headOption.map(t => t.node.id -> trees).toList
    val displayMap = calculateDisplayMap(treeList)

    // we assume edges are well formed directionally (from frontend)
    // this is largely because we're just dealing with traverse edges
    def getEndpoint(item: String, edges: List[EdgeClause]): List[String] = {
      val hasEdges = edges.filter { e =>
        // this is a bit dangerous
        e.from =?= item && BuilderEdgeType.fromPredicate(e.predicate).priority =?= BuilderEdgePriority.Required
      }

      val nextEdges = edges.filterNot(_.from =?= item)

      if (hasEdges.isEmpty) {
        item :: Nil
      } else {
        hasEdges.flatMap { ee =>
          getEndpoint(ee.to, nextEdges)
        }
      }
    } // traverse edges until no edges?

    val remapEdges = displayMap.flatMap(_._2).map(_.bodyMap(externalNodes)).foldLeft(Map.empty[String, String])(_ ++ _)
    remapEdges.toList.flatMap {
      case (child, parent) => getEndpoint(child, edges).headOption.map { to => 
        (parent, child, to)
      }
    }
  }

  def applyOperation(op: BuilderOperation) = {
    op.`type` match {
      case BuilderOperationType.SetQuery => {
        val query = op.payload.as[SrcLogCodeQueryDTO].toModel
        BuilderState(
          query,
          None,
          Map.empty[String, Boolean], 
          this.trace,
          update = true,
        ).recalculateSelected
      }
      case BuilderOperationType.SetSrcLog => {
        val srclogPayload = op.payload.as[String]
        val query = SrcLogCodeQuery.parseOrDie(srclogPayload, this.query.language)

        BuilderState(
          query,
          None, 
          Map.empty[String, Boolean], 
          this.trace,
          update = true,
        ).recalculateSelected
      }
      case BuilderOperationType.AddPayload => {
        val payload = op.payload.as[FullPayload]
        val (idMap, nodes, edges, aliases) = payload.clauses(query)

        val baseNextQuery = query.addNodes(nodes).addEdges(edges).addAliases(aliases)

        val nextQuery = payload.defaultedReplace match {
          case true => {
            val endpoints = calculateContainsRemap(edges)
            baseNextQuery.remapContainsEdges(endpoints)
          }
          case false => baseNextQuery
        }

        val payloadSelected = payload.selected.flatMap {
          case Left(i) => idMap.get(i)
          case Right(i) => Some(i)
        }
        val nextSelected = selectPayload(nextQuery, payloadSelected)

        this.copy(
          query = nextQuery,
          selected = nextSelected,
          update = true
        ).recalculateSelected
      }
      case BuilderOperationType.AddArg => {
        val id = (op.payload \ "id").as[String]
        val name = (op.payload \ "name").as[String]
        val index = (op.payload \ "index").asOpt[Int]
        val predicate = (op.payload \ "predicate").as[EdgePredicate]

        val nextId = query.nextVertexId

        val trees = BuilderTree.buildFromQuery(query)
        trees.flatMap(_.followTo(id)).headOption match {
          case Some(f) => {
            val edge = EdgeClause(
              predicate = predicate,
              from = id,
              to = nextId,
              condition = index.map(i => IndexCondition(i)),
              modifier = None
            )

            this.copy(
              query = query.addEdges(List(edge)).addAlias(nextId, name),
              // explicitly do not change selected for function arg
              update = true
            ).recalculateSelected
          }
          case None => {
            // ERROR: do nothing
            this
          }
        }
      }      
      case BuilderOperationType.Delete => {
        val id = op.payload.as[String]

        val trees = SrcLogOperations.extractComponents(query).find(_._2.vertexes.contains(id)).toList.flatMap {
          case (_, v) => BuilderTree.buildFromQuery(v)
        }

        val allIds = trees.flatMap(_.followTo(id)).flatMap(_.allIds)

        // any contains edges into allIds need to be remapped to id parent
        // get parent of id

        // Assumes well formed existing query
        val parent = query.edges.find { e => 
          e.to =?= id && BuilderEdgeType.fromPredicate(e.predicate).priority =?= BuilderEdgePriority.Required
        }.map(_.from)

        val nextQuery = parent match {
          case Some(p) => {
            val parentEdges = query.edges.filter { e =>
              BuilderEdgeType.fromPredicate(e.predicate).isContains && allIds.contains(e.to)
            }

            println("EDGES", p, query.edges)
            val remapList = parentEdges.map { pp => 
              (pp.from, pp.to, p)
            }
            println("REMAP", remapList)
            query.remapContainsEdges(remapList).delete(allIds)
          }
          case _ => {
            query.delete(allIds)
          }
        }

        this.copy(
          nextQuery,
          update = true
        ).recalculateSelected
      }
      case BuilderOperationType.DeleteEdge => {
        val from = (op.payload \ "from").as[String]
        val to = (op.payload \ "to").as[String]

        this.copy(
          query.deleteEdge(from, to),
          update = true          
        ).recalculateSelected
      }      
      case BuilderOperationType.SetName => {
        val id = (op.payload \ "id").as[String]
        val name = (op.payload \ "name").as[String]

        this.copy(
          query.rename(id, name),
          update = true
        )
      }
      case BuilderOperationType.UnsetName => {
        val id = op.payload.as[String]

        this.copy(
          query.unsetName(id),
          update = true
        )
      }      
      case BuilderOperationType.SetIndex => {
        val id = (op.payload \ "id").as[String]
        val parentId = (op.payload  \ "parentId").asOpt[String]
        val index = (op.payload \ "index").as[Int]

        this.copy(
          query.setIndex(id, parentId, index),
          update = true
        )
      }
      case BuilderOperationType.UnsetIndex => {
        val id = (op.payload  \ "id").as[String]
        val parentId = (op.payload  \ "parentId").asOpt[String]

        this.copy(
          query.unsetIndex(id, parentId),
          update = true
        )
      }
      case BuilderOperationType.PrependIndex => {
        // only used for function arg dragging
        val parentId = (op.payload \ "parent").as[String]
        val orig = (op.payload \ "original").as[Int]
        val destOpt = (op.payload \ "index").asOpt[Int]

        val trees = BuilderTree.buildFromQuery(query)

        trees.flatMap(_.followTo(parentId)).headOption match {
          case Some(f) if Option(orig) =/= destOpt => {
            val argChildren = f.children.flatMap { child =>
              val rightType = child.edgeType =?= JavascriptBuilderEdgeType.FunctionArg || child.edgeType =?= JavascriptBuilderEdgeType.MethodArg
              child.index match {
                case Some(idx) if rightType => {
                  Some(idx -> child)
                }
              case _ => None
              }
            }

            val dest = destOpt match {
              case Some(d) if d > orig => d - 1
              case Some(d) => d
              case None => {
                argChildren.map(_._1).maxByOption(i => i).getOrElse(0)
              }
            }

            val newQuery = argChildren.foldLeft(query) {
              case (q, (idx, child)) if idx =?= orig => q.setIndex(child.id, None, dest)
              // before everything
              case (q, (idx, child)) if idx < math.min(orig, dest) => q
              case (q, (idx, child)) if idx > math.max(orig, dest) => q
              // shift cases
              case (q, (idx, child)) if orig < dest && idx <= dest => q.setIndex(child.id, None, idx - 1)
              case (q, (idx, child)) if orig > dest && idx < orig => q.setIndex(child.id, None, idx + 1)
              // child does not have index
              case _ => throw new Exception(s"invalid case")
            }

            this.copy(
              query = newQuery,
              update = true              
            )
          }
          case _ => {
            // ERROR: do nothing
            this
          }
        }
      }      
      case BuilderOperationType.SetAlias => {
        val id = (op.payload \ "id").as[String]
        val alias = (op.payload \ "alias").as[String]

        this.copy(
          query.addAlias(id, alias)
          // update = false
        )
      }
      case BuilderOperationType.Select => {
        val id = op.payload.as[String]

        if (trace) {
          this.copy(
            traceSelected = traceSelected + (id -> true),
            selected = Some(id)
          ).recalculateSelected
        } else {
          this.copy(
            selected = Some(id)
          ).recalculateSelected
        }
      }
      case BuilderOperationType.Deselect => {
        val id = op.payload.as[String]

        this.copy(
          traceSelected = traceSelected + (id -> false)
        )
      }
    }
  }


  private def calculateDisplayMap(trees: List[(String, List[BuilderTree])]) = {
    // assumption: there are no cross tree references    
    println("====DISPLAY====")
    // println("INPUT")
    // pprint.pprintln(trees)

    // First pass: calculate reference dependencies
    val firstPass = trees.map {
      case (k, vs) => {
        // calculate references based on trees
        val vIdMap = vs.flatMap { v =>
          v.allIdsWithoutReferences.map { id =>
            id -> v.node.id
          }
        }.toMap

        val vRefMap = vs.flatMap { v =>
          v.allReferences.map { id =>
            id -> v.node.id
          }
        }.toMap

        val referenceCounts = vRefMap.toList.groupBy(_._1).map {
          case (k, vv) => k -> vv.length
        }

        val dependencyMap = referenceCounts.keySet.toList.flatMap { ref =>
          val from = vRefMap.get(ref)
          val to = vIdMap.get(ref)

          (from, to) match {
            case (Some(f), Some(t)) if f =/= t => Some((t, f)) // refFrom dependency on refTo
            case _ => None
          }
        }

        val sortOrder = {
          silvousplay.TSort.topologicalSort(dependencyMap).toList
        }

        val sortedVs = vs.sortBy { v => 
          sortOrder.indexOf(v.node.id)
        }

        k -> (sortedVs, referenceCounts)
      }
    }.sortBy(_._1).toMap

    // println("FIRST PASS")
    // pprint.pprintln(firstPass)

    // Reference splicing
    // Get true references (no alias)
    val allReferenceCounts = firstPass.values.map(_._2).foldLeft(Map.empty[String, Int]) {
      case (acc, next) => {
        val added = next.map {
          case (k, v) => k -> (acc.getOrElse(k, 0) + v)
        }

        acc ++ added
      }
    }

    // force references into definecontainers
    val secondPass = firstPass.map {
      case (k, (vs, referenceCounts)) => {
        val requireDependencySet = vs.flatMap(_.children.flatMap(_.allRequire)).toSet
        val forced = vs.map(_.forceReferences(referenceCounts.keySet))
        k -> forced.map(_.display("").forceDependency(requireDependencySet))
      }
    }

    // calculate definition map for replacing
    val defineMap = {
      val allDisplays = secondPass.values.flatten

      val flattenedDisplays = {
        allDisplays.flatMap(_.flattenedChildren).map { d =>
          d.id -> d
        }.filter { 
          case (k, v) => (allReferenceCounts.getOrElse(k, 0) =?= 1) && !v.forceReference
        }
      }

      flattenedDisplays.foldLeft(Map.empty[String, DisplayStruct]) {
        case (acc, (k, d)) => {
          acc + (k -> d.replaceReferences(acc))
        }
      }
    }

    // println("SECOND PASS")
    // pprint.pprintln(secondPass)
    // pprint.pprintln(defineMap)

    secondPass.map {
      case (k, vs) => k -> vs.map(_.replaceReferences(defineMap)) // flatMap // delete def in replace references
    }.toList.sortBy(_._1) // for consistency
  }

  def dto = {
    val components = SrcLogOperations.extractComponents(query)

    val trees = components.map {
      case (k, v) => k -> BuilderTree.buildFromQuery(v)
    }

    /**
      * Selection with result trees
      */
    val resultKeys = trees.flatMap {
      case (k, vs) => {
        // List[T]
        for {
          resultKey <- vs.headOption.map(_.node.id).toList
          id <- vs.flatMap(_.allIds).distinct
        } yield {
          id -> k
        }
      }
    }.toMap

    val resultKey: Option[String] = {
      selected.flatMap(resultKeys.get)
    }

    // Option[T]
    val maybeChain = for {
      s <- this.selected
      tree <- trees.flatMap(_._2).find(_.allIds.contains(s))
    } yield {
      val parents = tree.allIdsTo(s, Nil)
      val children = tree.followTo(s).toList.flatMap(_.children.flatMap(_.tree.allIds).distinct)
      (parents, children)
    }

    val (parentChain, children) = maybeChain.getOrElse((Nil, Nil))

    /**
      * Bleh
      */
    val vertexes = query.vertexes

    val referenceMap = vertexes.map { v =>
      val treeString = trees.flatMap(_._2).find(_.allIds.contains(v)).flatMap {
        _.followTo(v).map(_.display("").chunks(0, Nil).flatMap(_.display).mkString)
      }.getOrElse(v)
      // wildly inefficient
      v -> treeString
    }.toMap

    val defaultedTraceSelected = vertexes.toList.map { v => 
      v -> true
    }.toMap ++ traceSelected

    // calculate displays
    val displayMap = calculateDisplayMap(trees)

    // pass defaultedSelected to chunks
    // dropRight is a hack, but should work
    val chunks = displayMap.flatMap {
      case (k, vs) => {
        // filter out empty emptycontainers
        val filteredVs = vs.flatMap(_.filterEmpty)
        // wrap in empty container to get traverse item
        EmptyContainer(filteredVs).chunks(0, Nil) :+ StaticDisplayItem.NewLine(2, 0)
      }
    }.dropRight(1)

    val (_, highlights) = chunks.foldLeft((0, List.empty[HighlightDTO])) {
      case ((startIdx, acc), nextChunk) => {
        val (nextIndex, nextObjs) = nextChunk.apply(startIdx)
        (nextIndex, acc ++ nextObjs)
      }
    }

    BuilderStateDTO(
      query.dto,
      chunks.map(_.display).mkString(""),
      highlights,
      SrcLogString.stringify(query),
      query.aliases.groupBy(_._2).map {
        case (k, vs) => k -> vs.map(_._1).toList
      },
      //
      referenceMap,
      resultKeys,
      //
      resultKey,
      selected, // should be non-null as long as there is a query
      defaultedTraceSelected,
      update,
      //
      parentChain,
      children
    )
  }
}

case class BuilderStateDTO(
  state:      SrcLogCodeQueryDTO,
  display:    String,
  highlights: List[HighlightDTO],
  srclog:     String,
  aliases:    Map[String, List[String]],
  // helper
  referenceMap: Map[String, String],
  resultKeys: Map[String, String],
  //
  resultKey: Option[String],
  selected: Option[String],
  traceSelected: Map[String, Boolean],  
  // update flag: this mediates whether or not we rerun the query
  update: Boolean,
  // selection
  parents: List[String],
  children: List[String]
)

object BuilderStateDTO {
  implicit val writes = Json.writes[BuilderStateDTO]
}
