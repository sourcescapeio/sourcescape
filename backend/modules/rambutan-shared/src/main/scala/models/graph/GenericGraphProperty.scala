package models.graph

import play.api.libs.json._
import silvousplay.imports._

case class GenericGraphProperty(
  key:   String,
  value: String) {

  def encode = Keyable.encode(key) + "//" + Keyable.encode(value)
}

object GenericGraphProperty {
  implicit val reads: Reads[GenericGraphProperty] = Reads.StringReads.flatMapResult { s =>
    s.split("//") match {
      case Array(k, v) => JsSuccess(GenericGraphProperty(Keyable.decode(k), Keyable.decode(v)))
      case _           => JsError("invalid property")
    }
  }

  implicit val writes: Writes[GenericGraphProperty] = Writes { p =>
    JsString(p.encode)
  }
}
