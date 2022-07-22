package models.query.grammar

import models.IndexType
import silvousplay.imports._
import play.api.libs.json._

sealed trait ReturnStrategy {
  def returnObj: Option[String]
}

class PayloadReturnStrategy(val payloadType: PayloadType, names: => List[String]) extends ReturnStrategy {
  def returnObj = {
    val predicates = Json.stringify(Json.toJson(payloadType.allPredicates))
    val base = s"""{
    type: '${payloadType.identifier}',
${names.map("    " + _ + ",").mkString("\n")}
    predicates: ${predicates}
  }"""
    Some(base)
  }
}

class DictReturnStrategy(names: => List[String]) extends ReturnStrategy {
  def returnObj = {
    val base = s"""{
${names.map("    " + _ + ",").mkString("\n")}
  }"""
    Some(base)
  }
}

case class SingleReturnStrategy(name: String) extends ReturnStrategy {
  def returnObj = Some(name)
}

case object NoReturnStrategy extends ReturnStrategy {
  def returnObj = None
}

case class GrammarBuilder(id: String) {
  def payload(typ: PayloadType, ret: (String, String)*)(peg: => Peg) = {
    new Grammar(
      id,
      peg,
      returnStrategy = new PayloadReturnStrategy(typ, {
        ret.toList match {
          case Nil => peg.segments.flatMap(_.name)
          case nonEmpty => nonEmpty.map {
            case (k, v) => s"${k}: ${v}"
          }
        }
      }),
      constraint = None)
  }

  def dict(ret: (String, String)*)(peg: => Peg) = {
    new Grammar(
      id,
      peg,
      returnStrategy = new DictReturnStrategy({
        ret.toList match {
          case Nil => peg.segments.flatMap(_.name)
          case nonEmpty => nonEmpty.map {
            case (k, v) => s"${k}: ${v}"
          }
        }
      }),
      constraint = None)
  }

  def single(name: String)(peg: => Peg) = {
    new Grammar(
      id,
      peg,
      returnStrategy = SingleReturnStrategy(name),
      constraint = None)
  }

  def onlyPattern(peg: => Peg) = {
    new Grammar(
      id,
      peg,
      returnStrategy = NoReturnStrategy,
      constraint = None)
  }

  // composites
  def or(grammars: Grammar*) = {
    new Grammar(
      id,
      Peg(MultiGrammar(grammars.toList) :: Nil),
      returnStrategy = NoReturnStrategy,
      constraint = None)
  }
}

class Grammar(id: String, peg: => Peg, returnStrategy: ReturnStrategy, constraint: Option[String]) {

  override def hashCode(): Int = id.hashCode()

  override def equals(other: Any) = other match {
    case gram: Grammar => gram.getId =?= this.id
    case _             => false
  }

  // main
  def getId = id
  def getPeg = peg
  def getReturnStrategy = returnStrategy

  // additions
  def constraint(const: String) = {
    new Grammar(id, peg, returnStrategy, constraint = Some(const))
  }

  def s = GrammarSegment(this)

  def fullPattern = {
    val patternLine = id + " = " + peg.pattern
    val constraintLine = constraint match {
      case None => ""
      case Some(inner) => {
        val innerFlattened = inner.split("\n")
        " & {\n" + innerFlattened.map("  " + _).mkString("\n") + "\n}"
      }
    }
    val returnLine = returnStrategy.returnObj match {
      case None => ""
      case Some(inner) =>
        " {\n" + "  return " + inner + "\n}"
    }
    s"${patternLine}${constraintLine}${returnLine}"
  }

  def dependencies: Set[Grammar] = {
    def recurseDependencies(current: Grammar, existing: Set[Grammar]): Set[Grammar] = {
      val newDependencies = current.getPeg.dependencies.toSet -- existing

      // newDependencies.map(_.getId).foreach(println)

      val traversedNew = newDependencies.flatMap { n =>
        recurseDependencies(n, existing ++ newDependencies)
      }

      existing ++ traversedNew
    }

    recurseDependencies(this, Set.empty[Grammar])
  }

  def pegJs = {
    (this :: this.dependencies.toList).map(_.fullPattern).mkString("\n\n")
  }

  // get dependencies
}

case class Peg(segments: List[PegSegment]) extends PegSegment {
  // reuse
  def pattern = segments.map(_.pattern).mkString(" ")
  def name = None

  def ~(other: PegSegment) = {
    this.copy(segments :+ other)
  }

  override def dependencies = {
    segments.flatMap(_.dependencies)
  }
}

object Peg {
  def empty = Peg(Nil)
}

sealed trait PegSegment {
  def pattern: String
  def name: Option[String]

  def dependencies: List[Grammar] = Nil
}

case class PatternSegment(pattern: String) extends PegSegment {
  def name = None
}

case class GroupedSegment(peg: Peg) extends PegSegment {
  def pattern = "(" + peg.pattern + ")"
  def name = None
  override def dependencies = peg.dependencies
}

case class KeywordSegment(keyword: String) extends PegSegment {
  def pattern = "\"" + keyword + "\""
  def name = None
}

case class MultiSegment(inner: List[Peg]) extends PegSegment {
  def pattern = "(" + inner.map(_.pattern).mkString(" / ") + ")"
  def name = None
}

case class NamedSegment(nameIn: String, inner: PegSegment) extends PegSegment {
  def pattern = s"${nameIn}:${inner.pattern}"

  def name = Some(nameIn)

  override def dependencies = inner.dependencies
}

case class GrammarSegment(inner: Grammar) extends PegSegment {
  def pattern = inner.getId
  def name = None

  override def dependencies = inner :: Nil
}

// should we unify?
case class MultiGrammar(inner: List[Grammar]) extends PegSegment {
  def pattern = inner.map(_.getId).mkString(" / ")
  def name = None

  override def dependencies = inner
}

case class RepeatedSegment(inner: PegSegment, repeat: String) extends PegSegment {

  def pattern = inner match {
    case i: KeywordSegment => s"${inner.pattern}${repeat}"
    case _                 => s"(${inner.pattern})${repeat}"
  }

  def name = None // we clear out

  override def dependencies = inner.dependencies
}

object Grammar {
  private def renderParsers[T](parserTypes: Seq[ParserType])(f: ParserType => T) = {
    parserTypes.map { parserType =>
      parserType.identifier -> f(parserType)
    }.toMap
  }

  val GrammarMap = Map[IndexType, Plenumeration[ParserType]](
    IndexType.Javascript -> JavascriptParserType,
    IndexType.Scala -> ScalaParserType,
    IndexType.Ruby -> RubyParserType)

  private val autocompletes = Map(
    IndexType.Javascript -> AutocompleteType.render(JavascriptAutocompleteType.all),
    IndexType.Scala -> AutocompleteType.render(ScalaAutocompleteType.all),
    IndexType.Ruby -> AutocompleteType.render(RubyAutocompleteType.all))

  private def buildGrammarMap(in: Map[IndexType, Plenumeration[ParserType]]) = {
    in.map {
      case (k, v) => k -> renderParsers(v.all)(_.start.pegJs)
    }
  }

  private def buildIncompleteMap(in: Map[IndexType, Plenumeration[ParserType]]) = {
    in.map {
      case (k, v) => k -> renderParsers(v.all)(_.incompleteStart.pegJs)
    }
  }

  private def buildInitialMap(in: Map[IndexType, Plenumeration[ParserType]]) = {
    in.map {
      case (k, v) => k -> renderParsers(v.all) { parserType =>
        parserType.flattenedPayloads.map(_.toJson(Json.obj()))
      }
    }
  }

  def generateGrammarPayload() = {
    val grammarMap = buildGrammarMap(GrammarMap)
    val incompleteMap = buildIncompleteMap(GrammarMap)
    val initialMap = buildInitialMap(GrammarMap)

    // NOTE: mapping all these PayloadTypes because we don't want to create a unifying writes
    Json.obj(
      "grammars" -> Json.toJson(grammarMap),
      "incompletes" -> Json.toJson(incompleteMap),
      "initial" -> Json.toJson(initialMap),
      "autocompletes" -> Json.toJson(autocompletes),
      "payloads" -> Json.toJson(Map(
        "universal" -> BasePayloadType.all.map(_.identifier),
        IndexType.Javascript.identifier -> JavascriptPayloadType.all.map(_.identifier),
        IndexType.Scala.identifier -> ScalaPayloadType.all.map(_.identifier),
        IndexType.Ruby.identifier -> RubyPayloadType.all.map(_.identifier))))
  }

}