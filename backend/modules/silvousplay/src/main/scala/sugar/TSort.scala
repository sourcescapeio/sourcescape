package silvousplay

import scala.annotation.tailrec
import silvousplay.imports._

object TSort {
  def trace[A](edges: Iterable[(A, A)], start: A): List[A] = {
    edges.find(_._2 =?= start) match {
      case Some((from, id)) => id :: trace(edges, from)
      case None             => List(start)
    }
  }

  //https://gist.github.com/ThiporKong/4399695
  //NOTE: No dependencies last
  def topologicalSort[A](edges: Iterable[(A, A)]): Iterable[A] = {
    @tailrec
    def tsort(toPreds: Map[A, Set[A]], done: Iterable[A]): Iterable[A] = {
      val (noPreds, hasPreds) = toPreds.partition { _._2.isEmpty }
      if (noPreds.isEmpty) {
        if (hasPreds.isEmpty) {
          done
        } else {
          throw new Exception(s"""Invalid sort ${hasPreds.toString}""")
        }
      } else {
        val found = noPreds.map { _._1 }
        tsort(hasPreds.view.mapValues { _ -- found }.toMap, done ++ found)
      }
    }

    val toPred = edges.foldLeft(Map[A, Set[A]]()) { (acc, e) =>
      val (e1, e2) = e
      acc + (e1 -> acc.getOrElse(e1, Set())) + (e2 -> (acc.getOrElse(e2, Set()) + e1))
    }

    tsort(toPred, Seq())
  }
}
