package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait AnalysisTreeTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class AnalysisTreeTable(tag: Tag) extends Table[AnalysisTree](tag, "analysis_tree") with SafeIndex[AnalysisTree] {
    def indexId = column[Int]("index_id")
    def file = column[String]("file")
    def analysisType = column[AnalysisType]("analysis_type")

    def pk = withSafePK(s => primaryKey(s, (indexId, file, analysisType)))
    def indexIdx = withSafeIndex("sha")(s => index(s, indexId))

    def * = (indexId, file, analysisType) <> (AnalysisTree.tupled, AnalysisTree.unapply)
  }

  object AnalysisTreeTable extends SlickDataService[AnalysisTreeTable, AnalysisTree](TableQuery[AnalysisTreeTable]) {
    object byIndex extends Lookup[Int, List](_.indexId)(_.indexId)
  }
}
