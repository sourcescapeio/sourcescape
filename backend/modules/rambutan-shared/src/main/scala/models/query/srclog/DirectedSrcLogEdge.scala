package models.query

import silvousplay.imports._

case class DirectedSrcLogEdge(
  from:            String,
  to:              String,
  predicate:       EdgePredicate,
  condition:       Option[Condition],
  booleanModifier: Option[BooleanModifier],
  reverse:         Boolean,
  // additional checks
  nodeCheck:    Option[NodeClause],
  containsHint: Option[NodePredicate]) {

  def edgePenalty = {
    if (reverse) {
      predicate.reverseCost
    } else {
      predicate.forwardCost
    }
  }

  def propogatedFollow = {
    withFlag(reverse && nodeCheck.isEmpty) {
      // predicate.propogatedFollow
    }
  }

  def edgeTraverse = {
    val index = condition match {
      case Some(IndexCondition(v)) => Some(v.toInt)
      case _                       => None
    }
    val name = condition match {
      case Some(NameCondition(v)) => Some(v)
      case _                      => None
    }
    // TODO?
    // val multiName = condition match {
    //   case Some(MultiNameCondition(p)) => p
    //   case _                           => Nil
    // }
    val props = condition match {
      case Some(GraphPropertyCondition(p)) => p
      case _                               => Nil
    }

    // val baseTraverse = predicate.queryTraverse(name, index, props, Nil)
    // val

    // final def reverseTraverse(name: Option[String], index: Option[Int], props: List[GenericGraphProperty], follow: List[GraphEdgeType]): List[Traverse] = {
    //   ReverseTraverse(
    //     EdgeTypeFollow(follow.map(EdgeTypeTraverse.basic)),
    //     queryTraverse(name, index, props, Nil)) :: Nil
    // }

    if (reverse) {
      // TODO: this logic is not correct.
      // should not always follow, but should look at flag on previous predicates
      // val follow = withFlag(predicate.egressReferences || true) {
      //   EdgeTypeTraverse.BasicFollows
      // }

      predicate.reverseTraverse(name, index, props, Nil)
    } else {
      // val follow = withFlag(predicate.ingressReferences) {
      //   EdgeTypeTraverse.BasicFollows
      // }

      predicate.queryTraverse(name, index, props, Nil)
      // base match {
      //   case head :: rest => head.copy(follow = new EdgeTypeFollow(follow)) :: rest
      //   case Nil          => throw new Exception("improperly defined predicate")
      // }
    }
  }

  def nodeTraverse = {
    withDefined(nodeCheck) { nc =>
      ifNonEmpty(nc.filters) {
        List(
          NodeTraverse(
            follow = EdgeTypeFollow(Nil), // should use self.propagated follows
            filters = nc.filters))
      }
    }
  }

  /**
   * Deprecate below
   */
  @deprecated
  def toTraceQuery = {
    // these conditions should never happen
    if (booleanModifier.isDefined && reverse) {
      throw new Exception("INVALID: boolean edges must be forward facing")
    }
    // if (predicate.singleDirection && !reverse) {
    //   throw new Exception("INVALID: contains-type edges must be reverse facing")
    // }

    val index = condition match {
      case Some(IndexCondition(v)) => Some(v.toInt)
      case _                       => None
    }
    val name = condition match {
      case Some(NameCondition(v)) => Some(v)
      case _                      => None
    }
    val multiName = condition match {
      case Some(MultiNameCondition(p)) => p
      case _                           => Nil
    }
    val props = condition match {
      case Some(GraphPropertyCondition(p)) => p
      case _                               => Nil
    }

    // apply modifiers
    val withReverse = if (reverse) {
      // TODO: this logic is not correct.
      // should not always follow, but should look at flag on previous predicates
      val follow = withFlag(predicate.egressReferences || true) {
        EdgeTypeTraverse.BasicFollows
      }

      predicate.reverseTraverse(name, index, props, follow)
    } else {
      val follow = withFlag(predicate.ingressReferences) {
        EdgeTypeTraverse.BasicFollows
      }

      predicate.queryTraverse(name, index, props, follow)
      // base match {
      //   case head :: rest => head.copy(follow = new EdgeTypeFollow(follow)) :: rest
      //   case Nil          => throw new Exception("improperly defined predicate")
      // }
    }
    val withNodeCheck = nodeCheck match {
      case Some(nc) => withReverse ++ nodeTraverse
      case _        => withReverse
    }

    val withContainsHint = containsHint match {
      case Some(ch) => withNodeCheck.map {
        case e @ EdgeTraverse(_, EdgeTypeTarget(target :: Nil), _) if target.edgeType.isContainsForward => {
          val typeHint = ch match {
            case s: SimpleNodePredicate => Some(s.nodeType)
            case _                      => None
          }
          e.copy(
            typeHint = typeHint)
        }
        case other => other
      }
      case _ => withNodeCheck
    }

    val leftJoin = booleanModifier.isDefined

    KeyedQuery(
      to,
      TraceQuery(
        FromRoot(from, leftJoin = leftJoin),
        withContainsHint))
  }

  def forceForwardDirection = predicate.forceForwardDirection || booleanModifier.isDefined

  def cost = {
    if (predicate.singleDirection && !reverse && containsHint.isEmpty) {
      100
    } else if (predicate.singleDirection && !reverse) {
      20
    } else if (predicate.singleDirection) {
      2
    } else {
      1
    }
  }

  // node traversal
  def intoImplicit = if (reverse) {
    predicate.fromImplicit
  } else {
    predicate.toImplicit
  }

  def mustNodeTraverse = {
    if (reverse) {
      predicate.ingressReferences
    } else {
      predicate.egressReferences
    }
  }

  //
  def equiv(other: EdgeClause) = {
    val (fromT, toT) = if (reverse) {
      (other.to, other.from)
    } else {
      (other.from, other.to)
    }
    (from =?= fromT) && (to =?= toT) && (predicate =?= other.predicate) && (condition =?= other.condition)
  }

  def flip(hintNode: Option[NodePredicate]) = this.copy(
    from = to,
    to = from,
    reverse = !reverse,
    containsHint = hintNode)
}

object DirectedSrcLogEdge {
  def reverse(edge: EdgeClause, nodeMap: Map[String, NodeClause]) = {
    val nodeCheck = nodeMap.get(edge.from)
    if (edge.predicate.mustSpecifyNodes && nodeCheck.isEmpty) {
      throw new Exception(s"More specific node needs to be defined for ${edge.from}")
    }
    DirectedSrcLogEdge(
      edge.to,
      edge.from,
      edge.predicate,
      edge.condition,
      edge.modifier,
      reverse = true,
      nodeCheck = nodeCheck,
      containsHint = None)
  }

  def forward(edge: EdgeClause, nodeMap: Map[String, NodeClause]) = {
    val nodeCheck = nodeMap.get(edge.to)
    if (edge.predicate.mustSpecifyNodes && nodeCheck.isEmpty) {
      throw new Exception(s"More specific node needs to be defined for ${edge.to}")
    }
    DirectedSrcLogEdge(
      edge.from,
      edge.to,
      edge.predicate,
      edge.condition,
      edge.modifier,
      reverse = false,
      nodeCheck = nodeCheck,
      containsHint = None)
  }
}
