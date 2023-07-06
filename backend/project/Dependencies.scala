import sbt._

object Dependencies {
  object Vsn {
    val Scala = "2.13.8"

    val Play = "2.8.18"

    val PlaySlick = "5.0.2"

    // TODO: some issues in upgrading due to Jackson dependencies
    val PostgresSlick = "0.19.7"

    // TODO: 2.6.19 once we upgrade Play
    val Akka = "2.5.31"
    val AkkaHttp = "10.1.15"
    // val Alpakka = "2.0.2"

    // TODO: could use an upgrade
    val TestContainersScala = "0.40.9"
  }

  object Play {
    // val datacommons = "com.typesafe.play" %% "play-datacommons" % Vsn.Play
    // val json = "com.typesafe.play" %% "play-json" % Vsn.Play

    // Need to force this dependency
    val twirl = "com.typesafe.play" %% "twirl-api" % "1.5.1"
  }

  object Core {
    val akkaStreams = "com.typesafe.akka" %% "akka-stream" % Vsn.Akka
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % Vsn.AkkaHttp
    // val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % Vsn.AkkaHttp

    val apacheCommons = "commons-io" % "commons-io" % "2.6"

    val sangria = "org.sangria-graphql" %% "sangria" % "2.0.1"
    val sangriaSlowLog = "org.sangria-graphql" %% "sangria-slowlog" % "2.0.1"
    val sangriaPlayJson = "org.sangria-graphql" %% "sangria-play-json" % "2.0.1"
    val sangriaAkkaStreams = "org.sangria-graphql" %% "sangria-akka-streams" % "1.0.2",
  }

  object Persist {
    val playSlick = "com.typesafe.play" %% "play-slick" % Vsn.PlaySlick
    val playSlickEvolutions = "com.typesafe.play" %% "play-slick-evolutions" % Vsn.PlaySlick
    val postgresSlick = "com.github.tminglei" %% "slick-pg" % Vsn.PostgresSlick
    val postgresSlickJson = "com.github.tminglei" %% "slick-pg_play-json" % Vsn.PostgresSlick

    val redis = "com.github.etaty" %% "rediscala" % "1.9.0"    
    // val pubsub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % Vsn.Alpakka
    // val gcs = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-storage" % Vsn.Alpakka
    // val s3 = "com.lightbend.akka" %% "akka-stream-alpakka-s3" % Vsn.Alpakka
  }

  object Parse {
    val fastParse = "com.lihaoyi" %% "fastparse" % "2.3.3"

    // TODO: upgrade to 4.5 concurrent with pbjson upgrade to 0.11
    val scalaMeta = "org.scalameta" %% "scalameta" % "4.5.9"
  }

  object Instrumentation {
    // TODO: replace with more modern instrumentation
    val sentry = "io.sentry" % "sentry-logback" % "1.7.29"

    val openTelemetry = "io.honeycomb" % "honeycomb-opentelemetry-sdk" % "1.3.0"

    // val logging = "com.google.cloud" % "google-cloud-logging-logback" % "0.116.0-alpha"
    // val zipkin = "jp.co.bizreach" %% "play-zipkin-tracing-play" % "3.0.1"
    // val prometheus = "io.prometheus" % "simpleclient" % "0.9.0"
  }

  object Misc {
    val scalaz = "org.scalaz" %% "scalaz-core" % "7.3.6"

    val pprint = "com.lihaoyi" %% "pprint" % "0.7.3"
    val scalaPbJson = "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.1"

    // val sprayJson = "io.spray" %%  "spray-json" % "1.3.3"
    // val jwt = "com.github.jwt-scala" %% "jwt-play-json" % "6.0.0"
    val bouncyCastleProv = "org.bouncycastle" % "bcprov-jdk15on" % "1.68"
    // val bouncyCastleKix = "org.bouncycastle" % "bcpkix-jdk15on" % "1.68"

    // val sendgrid = "com.sendgrid" % "sendgrid-java" % "4.3.0"

    val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r"

    val joda = "joda-time" % "joda-time" % "2.10.14"
  }

  object TestDeps {
    val scalaTestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
    val mockito = "org.mockito" %% "mockito-scala-scalatest" % "1.16.29" % Test
    val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Vsn.TestContainersScala % Test
    val testContainersPostgres = "com.dimafeng" %% "testcontainers-scala-postgresql" % Vsn.TestContainersScala % Test
    val testContainersElasticSearch = "com.dimafeng" %% "testcontainers-scala-elasticsearch" % Vsn.TestContainersScala % Test
  }
}
