package models.index

import silvousplay.imports._

// TODO: would be ideal to constraint further to node type / edge type
trait ValidEdgeConstraint[NT <: Identifiable, From <: GraphNodeData[NT], To <: GraphNodeData[NT], Z <: Identifiable]

trait CanIndexConstraint[T <: Identifiable]

trait CanNameConstraint[T <: Identifiable]
