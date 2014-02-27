import sbt._

import com.typesafe.sbt.web.SbtWebPlugin
import com.typesafe.sbt.jse.SbtJsTaskPlugin
import com.typesafe.sbt.mocha.SbtMochaPlugin
import xsbti.Severity

object TestBuild extends Build {

  object TestLogger extends Logger {
    val messages = new StringBuilder()

    def trace(t: => Throwable): Unit = {}

    def success(message: => String): Unit = {}

    def log(level: Level.Value, message: => String): Unit = messages ++= message
  }

  object TestReporter extends LoggerReporter(-1, TestLogger)

  lazy val root = Project(
    id = "test-build",
    base = file("."),
    settings =
      Project.defaultSettings ++
        SbtWebPlugin.webSettings ++
        SbtJsTaskPlugin.jsEngineAndTaskSettings ++
        SbtMochaPlugin.jshintSettings ++
        Seq(
          SbtWebPlugin.WebKeys.reporter := TestReporter,
          TaskKey[Unit]("check") := {
            val errorCount = TestReporter.count.get(Severity.Error)
            if (errorCount != 2) {
              sys.error(s"$errorCount linting errors received when 2 were expected.")
            }
            val messages = TestLogger.messages.toString()
            if (
              !messages.contains("Expected an assignment or function call and instead saw an expression.") &&
                !messages.contains("Missing semicolon.")
            ) {
              sys.error(messages)
            }
          }
        )
  )

}