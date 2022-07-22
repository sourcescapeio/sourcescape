package models

import org.joda.time._

object TimeFormatter {
  val formatter = format.DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  def str(in: Long) = formatter.print(new DateTime(in))
}
