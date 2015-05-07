sbtPlugin := true

organization := "com.typesafe.sbt"

name := "sbt-mocha"

version := "1.0.3-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.webjars" % "mocha" % "1.17.1"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.1.1")

publishMavenStyle := false

publishTo := {
  if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
  else Some(Classpaths.sbtPluginReleases)
}

scriptedSettings

scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }

scriptedLaunchOpts += "-XX:MaxPermSize=256m"
