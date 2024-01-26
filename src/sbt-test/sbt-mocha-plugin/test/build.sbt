enablePlugins(SbtWeb)

Keys.libraryDependencies += "org.specs2" %% "specs2-core" % "4.20.4" % "test"

MochaKeys.requires += "Setup"
scalaVersion := "2.13.12"

Global / Keys.extraLoggers := { scope =>
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
}

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
}

TaskKey[Unit]("resetLogs") := {
  TestLogger.messages = Nil
  TestLogger.exceptions = Nil
  MockListener.gotAnEvent = false
}

TaskKey[Unit]("noErrors") := {
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

TaskKey[Unit]("mockListenerInvoked") := {
  if (!MockListener.gotAnEvent) {
    throw new RuntimeException("Mock test report listener was not invoked")
  }
}
