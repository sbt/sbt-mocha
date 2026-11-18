lazy val main = (project in file("."))
  .enablePlugins(SbtWeb)
  .dependsOn(common % "compile;test->test")

lazy val common = (project in file("common"))
  .enablePlugins(SbtWeb)

ThisBuild / scalaVersion := {
  if (sbtVersion.value.startsWith("1.")) "2.12.21" else "3.9.0"
}
