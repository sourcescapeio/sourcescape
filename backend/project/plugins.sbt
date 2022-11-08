// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.18")

// Scalariform provides auto-formatting
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

// Draws out dependency graphs. Not strictly necessary
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

// Gives non-Play apps the "stage" command
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")
