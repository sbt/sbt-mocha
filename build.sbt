lazy val `sbt-mocha` = project in file(".")

organization := "com.typesafe.sbt"
name := "sbt-mocha"
description := "sbt mocha support"

scalaVersion := "2.10.5"
sbtPlugin := true

libraryDependencies ++= Seq(
  "org.webjars" % "mocha" % "1.17.1"
)
addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.1.3")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
scriptedLaunchOpts += "-XX:MaxPermSize=256m"

// Publish settings
publishMavenStyle := false
bintrayOrganization := Some("sbt-web")
bintrayRepository := "sbt-plugin-releases"
bintrayPackage := "sbt-mocha"
bintrayReleaseOnPublish := false
homepage := Some(url("https://github.com/sbt/sbt-mocha"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

// Release settings
releaseSettings
ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
ReleaseKeys.tagName := (version in ThisBuild).value
ReleaseKeys.releaseProcess := {
  import sbtrelease._
  import ReleaseStateTransformations._

  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    ReleaseStep(action = releaseTask(bintrayRelease in `sbt-mocha`)),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}

