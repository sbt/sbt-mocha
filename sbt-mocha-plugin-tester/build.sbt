lazy val root = (project in file(".")).addPlugins(SbtWeb)

libraryDependencies ++= Seq(
  "org.webjars" % "rjs" % "2.1.11-1-trireme" % "test",
  "org.webjars" % "squirejs" % "0.1.0" % "test"
)

MochaKeys.requires += "Setup"