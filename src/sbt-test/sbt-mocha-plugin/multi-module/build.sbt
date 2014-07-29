lazy val main = (project in file("."))
  .enablePlugins(SbtWeb)
  .dependsOn(common % "compile;test->test")

lazy val common = (project in file("common"))
  .enablePlugins(SbtWeb)
