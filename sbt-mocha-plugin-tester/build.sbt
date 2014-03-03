import com.typesafe.sbt.jse.SbtJsTaskPlugin._

webSettings

jsEngineAndTaskSettings

mochaSettings

JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

libraryDependencies ++= Seq(
  "org.webjars" % "requirejs-node" % "2.1.11-1" % "test",
  "org.webjars" % "squirejs" % "0.1.0" % "test"
)

MochaKeys.mochaRequires += "Setup"