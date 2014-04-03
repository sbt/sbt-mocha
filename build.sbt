sbtPlugin := true

organization := "com.typesafe.sbt"

name := "sbt-mocha"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers ++= Seq(
    "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
    Resolver.sonatypeRepo("snapshots"),
    "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
    Resolver.mavenLocal
    )

libraryDependencies ++= Seq(
  "org.webjars" % "mocha" % "1.17.1",
  "org.specs2" %% "specs2" % "2.2.2" % "test"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.0.0-SNAPSHOT")

scriptedSettings

scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }

scriptedLaunchOpts += "-XX:MaxPermSize=256m"

publishMavenStyle := false

publishTo := {
    val isSnapshot = version.value.contains("-SNAPSHOT")
    val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
    val (name, url) = if (isSnapshot)
                        ("sbt-plugin-snapshots", scalasbt + "sbt-plugin-snapshots")
                      else
                        ("sbt-plugin-releases", scalasbt + "sbt-plugin-releases")
    Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}
