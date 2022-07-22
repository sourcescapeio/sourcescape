import Dependencies._

version := "1.0-SNAPSHOT"

/**
  * Libraries
  */
lazy val silvousplay = Project("silvousplay", file("modules/silvousplay")).
  settings(APIBuild.libSettings: _*)

/**
  * Shared systems
  */
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

// lazy val rambutanIndexerShared = Project("rambutan-indexer-shared", file("modules/rambutan-indexer-shared")).
//   dependsOn(rambutanShared).
//   settings(APIBuild.rambutanSettings: _*)

// lazy val rambutanAPIShared = Project("rambutan-api-shared", file("modules/rambutan-api-shared")).
//   dependsOn(rambutanShared).
//   settings(APIBuild.rambutanSettings: _*)

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

// lazy val rambutanIndexer = (project in file("apps/rambutan-indexer")).
//   enablePlugins(PlayScala).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     name := "rambutan-indexer",
//     parallelExecution in Test := false,
//     PlayKeys.devSettings := Seq("play.server.http.port" -> "9002")    
//   ).
//   dependsOn(rambutanIndexerShared).
//   dependsOn(rambutanLocalShared).
//   dependsOn(silvousplay)

/**
 * Web
 */
// lazy val rambutanWeb = (project in file("apps/rambutan-web")).
//   enablePlugins(PlayScala).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     name := "rambutan-web",
//     parallelExecution in Test := false,
//     PlayKeys.devSettings := Seq("play.server.http.port" -> "9003")    
//   ).
//   dependsOn(rambutanAPIShared).
//   dependsOn(rambutanWebShared).
//   dependsOn(silvousplay)

// lazy val rambutanWebIndexer = (project in file("apps/rambutan-web-indexer")).
//   enablePlugins(PlayScala).
//   settings(APIBuild.rambutanSettings: _*).
//   settings(
//     name := "rambutan-web-indexer",
//     parallelExecution in Test := false,
//     PlayKeys.devSettings := Seq("play.server.http.port" -> "9002")    
//   ).
//   dependsOn(rambutanIndexerShared).
//   dependsOn(rambutanWebShared).
//   dependsOn(silvousplay)

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
