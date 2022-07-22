package models.query

object SrcLogOperations {

  def connectedComponents[T <: SrcLogQuery](query: T) = {
    val edges = query.edgeMap

    // connected components
    val vertexes = query.vertexes.toList

    def dfs(start: String, visited: Set[String]): Set[String] = {
      edges.getOrElse(start, Nil).filterNot(visited.contains) match {
        case Nil => visited + start
        case some => {
          some.foldLeft(visited) {
            case (vv, next) => {
              dfs(next, vv + start)
            }
          }
        }
      }
    }

    vertexes.foldLeft(List.empty[Set[String]]) {
      case (acc, v) if !acc.exists(_.contains(v)) => {
        // pop a vertex to dfs
        val visited = dfs(v, Set.empty[String])

        visited :: acc
      }
      case (acc, _) => acc // skip vertex
    }
  }

  def extractComponents[T <: SrcLogQuery](query: T): List[(String, T)] = {
    val cc = connectedComponents(query)

    cc.map { cSet =>
      cSet.head -> query.subset(cSet).asInstanceOf[T] // TODO: should be safe, but we should eliminate anyways
    }
  }
}