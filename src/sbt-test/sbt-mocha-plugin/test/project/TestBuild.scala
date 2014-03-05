import sbt._

import com.typesafe.sbt.web.SbtWebPlugin
import com.typesafe.sbt.jse.{SbtJsEnginePlugin, SbtJsTaskPlugin}
import com.typesafe.sbt.mocha.SbtMochaPlugin._

object TestBuild extends Build {


  object TestLogger extends AbstractLogger {

    // ANSI code stripping is broken, see https://github.com/sbt/sbt/issues/1143
    // so we return true and strip them ourselves
    override def ansiCodesSupported = true
    def stripAnsiCodes(s: String): String = {
      s.replaceAll("\033[^m]*m", "")
    }

    var messages = List.empty[(Level.Value, String)]
    var exceptions = List.empty[Throwable]
    var successes = List.empty[String]

    def trace(t: => Throwable) = exceptions = t :: exceptions

    def success(msg: => String) = successes = msg :: successes

    def log(level: Level.Value, msg: => String) = {
      messages = (level, stripAnsiCodes(msg)) :: messages
    }

    def getLevel = Level.Debug
    def setLevel(newLevel: Level.Value) = Unit
    def setTrace(flag: Int) = Unit
    def getTrace = 1
    def successEnabled = true
    def setSuccessEnabled(flag: Boolean) = Unit
    def control(event: ControlEvent.Value, message: => String) = Unit
    def logAll(events: Seq[LogEvent]) = events.foreach(log)
  }

  lazy val root = Project(
    id = "test-build",
    base = file(".")
  ).settings(
    SbtWebPlugin.webSettings: _*
  ).settings(
    SbtJsTaskPlugin.jsEngineAndTaskSettings:_*
  ).settings(
    mochaSettings: _*
  ).settings(

    // SbtJsEnginePlugin.JsEngineKeys.engineType := SbtJsEnginePlugin.JsEngineKeys.EngineType.Node,

    Keys.libraryDependencies += "org.specs2" %% "specs2" % "2.3.8" % "test",

    MochaKeys.requires += "Setup",

    Keys.extraLoggers := { scope =>
      // Configure extra loggers just for the mocha and test tasks
      if (scope.scope.task.fold({ tsk =>
        Seq(
          MochaKeys.mochaOnly.key,
          MochaKeys.mocha.key,
          MochaKeys.mochaExecuteTests,
          Keys.test.key
        ).contains(tsk)
      }, false, false)) {
        Seq(TestLogger)
      } else {
        Nil
      }
    },

    InputKey[Unit]("logged") := {
      val args = Def.spaceDelimited("<level> <msg>").parsed
      val level = Level.withName(args.head)
      val msg = args.tail.head

      val msgs = TestLogger.messages.collect {
        case (l, m) if m.trim == msg => l
      }

      if (msgs.contains(level)) {
        // Pass
      } else if (msgs.isEmpty) {
          throw new RuntimeException("No test logged with content '" + msg + "' messages were:\n" + TestLogger.messages.reverse.mkString("\n"))
      } else {
        throw new RuntimeException("No test logged with content '" + msg + "' at level " + level + ", but some matched at level(s) " + msgs)
      }
    },

    TaskKey[Unit]("reset-logs") := {
      TestLogger.messages = Nil
      TestLogger.exceptions = Nil
      TestLogger.successes = Nil
    },

    TaskKey[Unit]("no-errors") := {
      val errors = TestLogger.messages.filter {
        case (l, m) => l == Level.Error || l == Level.Warn
      }
      if (!errors.isEmpty) {
        throw new RuntimeException("Expected no errors, but got:\n" + errors.reverse.mkString("\n"))
      }
      if (!TestLogger.exceptions.isEmpty) {
        throw new RuntimeException("Expected no exceptions, but got:\n" + TestLogger.exceptions.reverse.mkString("\n"))
      }
    }

  )

}