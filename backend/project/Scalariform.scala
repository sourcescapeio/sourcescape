import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

object Scalariform {
  val settings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := FormattingPreferences().
      setPreference(AlignSingleLineCaseStatements, true).
      setPreference(AlignParameters, true).
      setPreference(IndentSpaces, 2)
  )
}
