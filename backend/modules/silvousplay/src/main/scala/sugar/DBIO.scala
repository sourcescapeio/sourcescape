package silvousplay

import scala.concurrent.ExecutionContext
import slick.dbio._

trait DBIOHelpers {
  val DBIO = silvousplay.DBIO
}

object DBIO {
  def serializeDBIO[A, B, E <: Effect](l: Iterable[A])(fn: A => slick.dbio.DBIOAction[B, NoStream, E])(implicit ec: ExecutionContext): DBIOAction[List[B], NoStream, E] = {
    // janky way of remapping effect type
    val initial = slick.dbio.DBIO.successful(List.empty[B]).flatMap[List[B], NoStream, E] { a =>
      slick.dbio.DBIO.successful(a)
    }
    l.foldLeft(initial) {
      (previousDB, next) =>
        for {
          previousResults <- previousDB
          nextResults <- fn(next)
        } yield previousResults :+ nextResults
    }
  }
}
