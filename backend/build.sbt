import Dependencies._

version := "1.0-SNAPSHOT"

/**
  * Libraries
  */
lazy val silvousplay = Project("silvousplay", file("modules/silvousplay")).
  settings(APIBuild.libSettings: _*)

lazy val rambutanShared = Project("rambutan-shared", file("modules/rambutan-shared")).
  dependsOn(silvousplay).
  settings(APIBuild.rambutanSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"
    )
  )

/**
 * Scripts
 */
// lazy val rambutanInitializer = (project in file("apps/rambutan-initializer")).
//   enablePlugins(JavaAppPackaging).
//   dependsOn(rambutanShared).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     mainClass := Some("scripts.Initializer")
//   )

// lazy val rambutanWebInitializer = (project in file("apps/rambutan-web-initializer")).
//   enablePlugins(JavaAppPackaging).
//   dependsOn(rambutanShared).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     mainClass := Some("scripts.Initializer")
//   )  

// lazy val rambutanGrammarWriter = (project in file("apps/rambutan-grammar-writer")).
//   enablePlugins(JavaAppPackaging).
//   dependsOn(rambutanShared).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     mainClass := Some("scripts.GrammarWriter")
//   )

/**
 * Local
 */
lazy val rambutanLocal = (project in file("apps/rambutan-local")).
  enablePlugins(PlayScala).
  settings(APIBuild.rambutanSettings: _*).
  settings(
    name := "rambutan-local",
    parallelExecution in Test := false,
    PlayKeys.devSettings := Seq("play.server.http.port" -> "9003")
  ).
  dependsOn(rambutanShared).
  dependsOn(silvousplay)

lazy val rambutanIndexer = (project in file("apps/rambutan-indexer")).
  enablePlugins(PlayScala).
  settings(APIBuild.rambutanSettings: _*).
  settings(
    name := "rambutan-indexer",
    parallelExecution in Test := false,
    PlayKeys.devSettings := Seq("play.server.http.port" -> "9002")    
  ).
  dependsOn(rambutanShared).
  dependsOn(silvousplay)

// /**
//  * End to end tests
//  */
// lazy val rambutanTest = (project in file("apps/rambutan-test")).
//   enablePlugins(PlayScala).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     name := "rambutan-test",
//     parallelExecution in Test := false
//   ).
//   dependsOn(rambutanAPIShared).
//   dependsOn(rambutanIndexerShared).
//   dependsOn(rambutanWebShared).
//   // dependsOn(rambutanLocalShared)
//   dependsOn(silvousplay)
