package dal

import silvousplay.TSort
import silvousplay.data
import silvousplay.imports._

trait DataAccessLayer
  extends data.HasProvider
  with data.TableComponent
  with data.HealthComponent {

  val allTables: List[data.CanInitializeTable]

  lazy val sortedAllTables = {
    // Topological sort table names
    val tsortGraph = allTables.flatMap { t =>
      t.foreignKeyStatements().keys.map { fk =>
        t.tableName -> fk.targetTable.tableName
      }
    }
    val tsortIndex = TSort.topologicalSort(tsortGraph).toList

    // Join back together
    allTables.sortWith { (a, b) =>
      val aIndex = tsortIndex.indexOf(a.tableName)
      val bIndex = tsortIndex.indexOf(b.tableName)
      if (aIndex < 0 || bIndex < 0) {
        true // doesn't matter as a or b is outside the scope
      } else {
        aIndex < bIndex
      }
    }
  }

  def all(): List[data.CanInitializeTable] = {
    sortedAllTables
  }
}
