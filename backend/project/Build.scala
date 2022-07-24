import sbt._
import scala.util.Properties.envOrNone
import Keys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

object APIBuild {

  import Dependencies._

  val libDependencies = Seq(
    // Core.akkaHttp,
    // Core.akkaSprayJson,
    Core.akkaStreams,
    play.sbt.PlayImport.evolutions,
    play.sbt.PlayImport.jdbc,
    play.sbt.PlayImport.ws,
    play.sbt.PlayImport.guice,
    Persist.playSlick,
    Persist.postgresSlick,
    Persist.postgresSlickJson,
    Instrumentation.sentry,
    // Instrumentation.prometheus,
    // Instrumentation.zipkin,
    Misc.joda,
    Misc.scalaz,
    // Misc.jwt,
    Misc.bouncyCastleProv,
    // Misc.bouncyCastleKix,
  )

  val rambutanDependencies = Seq(
    Core.apacheCommons,
    Core.sangria,
    Core.sangriaPlayJson,
    Core.sangriaSlowLog,
    Parse.fastParse,
    Parse.scalaMeta,
    Persist.redis,
    // Persist.s3,
    // Persist.pubsub,
    // Persist.gcs,
    Misc.pprint,
    Misc.jgit,
    Misc.scalaPbJson
    //
    // TestDeps.scalaTestPlay,
    // TestDeps.mockito,
    // TestDeps.testContainers,
    // TestDeps.testContainersPostgres,
    // TestDeps.testContainersElasticSearch
  )

  // Conservative. Should match with .bash_profile settings
  val defaultArgs = Seq(
    "-Xmx4096m",
    "-Xms2048M",
    "-Xss32m",
    "-XX:MaxPermSize=256m",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:+UseCodeCacheFlushing",
    "-XX:+UseCompressedOops",
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSClassUnloadingEnabled"
  )

  val pluginSettings = Scalariform.settings

  val baseSettings = Seq(
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    scalaVersion := Vsn.Scala,
    //"-Xfatal-warnings", 
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8"),
    javaOptions in (Test, run) ++= defaultArgs,
    javaOptions in Test ++= Seq("-Dlogger.resource=logback-test.xml")
    //External repos
//    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
  ) ++ pluginSettings

  val rambutanSettings = baseSettings ++ Seq(
    libraryDependencies ++= rambutanDependencies
  )

  val libSettings = baseSettings ++ Seq(
    libraryDependencies ++= libDependencies
  )

}
