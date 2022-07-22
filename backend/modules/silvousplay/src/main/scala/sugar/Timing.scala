package silvousplay

import scala.concurrent.{ ExecutionContext, Future }

object Timing {

  def forFuture[T](f: => Future[T])(implicit ec: ExecutionContext): Future[(Long, T)] = {
    val t1 = System.nanoTime
    for {
      res <- f
    } yield {
      val duration = (System.nanoTime - t1)
      (duration, res)
    }
  }

}

trait TimingHelpers {
  val Timing = silvousplay.Timing
}