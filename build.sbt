lazy val `sbt-mocha` = project in file(".")

enablePlugins(SbtWebBase)

sonatypeProfileName := "com.github.sbt.sbt-mocha" // See https://issues.sonatype.org/browse/OSSRH-77819#comment-1203625

description := "sbt mocha support"

developers += Developer(
  "playframework",
  "The Play Framework Team",
  "contact@playframework.com",
  url("https://github.com/playframework")
)

addSbtJsEngine("1.3.9")

libraryDependencies ++= Seq(
  "org.webjars.npm" % "node-require-fallback" % "1.0.0",
  "org.webjars.npm" % "mocha" % "10.2.0", // sync with src/main/resources/com/typesafe/sbt/mocha/mocha.js
  "org.webjars.npm" % "minimatch" % "9.0.5",
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
