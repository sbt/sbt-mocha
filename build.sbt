sbtPlugin := true

organization := "com.typesafe.sbt"

name := "sbt-mocha"

version := "1.0.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
    "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
    Resolver.sonatypeRepo("snapshots"),
    "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
    Resolver.mavenLocal
    )

libraryDependencies ++= Seq(
  "org.webjars" % "mocha" % "1.17.1"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.0.0")

publishMavenStyle := false

publishTo := {
  if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
  else Some(Classpaths.sbtPluginReleases)
}

scriptedSettings

scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }

scriptedLaunchOpts += "-XX:MaxPermSize=256m"
