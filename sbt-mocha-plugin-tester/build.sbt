import com.typesafe.sbt.jse.SbtJsTaskPlugin._

webSettings

jsEngineAndTaskSettings

mochaSettings

JsEngineKeys.engineType := JsEngineKeys.EngineType.Node
