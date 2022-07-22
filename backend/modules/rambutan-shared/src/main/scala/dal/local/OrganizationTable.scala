package dal

import models._
import silvousplay.data
import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.{ ExecutionContext, Future }

trait OrganizationTableComponent {
  self: data.HasProvider with data.TableComponent =>

  import api._

  class OrganizationTable(tag: Tag) extends Table[Organization](tag, "organization") with SafeIndex[Organization] {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def ownerId = column[Int]("owner_id")
    def name = column[String]("name")

    def * = (id, ownerId, name) <> (Organization.tupled, Organization.unapply)
  }

  object OrganizationTable extends SlickIdDataService[OrganizationTable, Organization](TableQuery[OrganizationTable]) {

  }
}
