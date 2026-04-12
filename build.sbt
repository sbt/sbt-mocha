lazy val `sbt-mocha` = project in file(".")

enablePlugins(SbtWebBase)

description := "sbt mocha support"

developers += Developer(
  "playframework",
  "The Play Framework Team",
  "contact@playframework.com",
  url("https://github.com/playframework")
)

addSbtJsEngine("1.4.0-M4")

libraryDependencies ++= Seq(
  "org.webjars.npm" % "node-require-fallback" % "1.0.0",
  "org.webjars.npm" % "mocha" % "10.2.0", // sync with src/main/resources/com/typesafe/sbt/mocha/mocha.js
  "org.webjars.npm" % "minimatch" % "10.0.3",
)

// Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
ThisBuild / dynverVTagPrefix := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  dynverAssertTagVersion.value
  s
}

scriptedLaunchOpts ++= Seq(
  "-Xmx512M",
  "-XX:MaxMetaspaceSize=512M",
)

crossScalaVersions := Seq("2.12.21", "3.8.3")
ThisBuild / (pluginCrossBuild / sbtVersion) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.12.9"
    case _      => "2.0.0-RC11"
  }
}

scalacOptions := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, major)) => Seq("-Xsource:3", "-release:8")
    case _                => Seq.empty
  }
}
