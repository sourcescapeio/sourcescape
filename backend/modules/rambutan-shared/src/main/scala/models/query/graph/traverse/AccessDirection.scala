package models.query

import silvousplay.imports._
import play.api.libs.json._

sealed abstract class AccessDirection(
  val identifier: String) extends Identifiable {

  def extract(obj: JsObject) = {
    (obj \ "_source" \ identifier).as[String]
  }

  def extractOpposite(obj: JsObject) = {
    (obj \ "_source" \ opposite).as[String]
  }

  def reverse: AccessDirection

  def opposite = reverse.identifier
}

object AccessDirection {
  case object To extends AccessDirection("to") {
    def reverse = AccessDirection.From
  }
  case object From extends AccessDirection("from") {
    def reverse = AccessDirection.To
  }
}
