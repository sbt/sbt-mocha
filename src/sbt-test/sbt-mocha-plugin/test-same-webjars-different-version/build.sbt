enablePlugins(SbtWeb)

Keys.libraryDependencies += "org.specs2" %% "specs2-core" % "4.20.4" % "test"

MochaKeys.requires += "Setup"

Global / Keys.extraAppenders := { scope =>
  // Configure extra loggers just for the mocha and test tasks
  if (scope.scope.task.fold({ tsk =>
    Seq(
      MochaKeys.mochaOnly.key,
      MochaKeys.mocha.key,
      MochaKeys.mochaExecuteTests,
      Keys.test.key
    ).contains(tsk)
  }, false, false)) {
    Seq(TestAppender)
  } else {
    Nil
  }
}

InputKey[Unit]("logged") := {
  val args = Def.spaceDelimited("<level> <msg>").parsed
  val level = Level.withName(args.head)
  val msg = args.tail.head

  // sbt 2.1 summaries count suites rather than individual examples.
  val suiteSummaries = Map(
    "Passed: Total 3, Failed 0, Errors 0, Passed 3" -> "passed: total 1, failed 0, errors 0, passed 1, cached 0",
    "Passed: Total 0, Failed 0, Errors 0, Passed 0" -> "passed: total 0, failed 0, errors 0, passed 0, cached 0",
    "Passed: Total 1, Failed 0, Errors 0, Passed 1" -> "passed: total 1, failed 0, errors 0, passed 1, cached 0",
    "Failed: Total 1, Failed 1, Errors 0, Passed 0" -> "failed: total 1, failed 1, errors 0, passed 0, cached 0",
    "Error: Total 1, Failed 0, Errors 1, Passed 0" -> "error: total 1, failed 0, errors 1, passed 0, cached 0",
    "Error: Total 6, Failed 1, Errors 1, Passed 4" -> "error: total 4, failed 1, errors 1, passed 2, cached 0",
    "Error: Total 9, Failed 1, Errors 1, Passed 7" -> "error: total 5, failed 1, errors 1, passed 3, cached 0",
    "Failed tests:" -> "failed tests:",
    "Error during tests:" -> "error during tests:"
  )
  val expected = if (sbtVersion.value.startsWith("2.")) suiteSummaries.getOrElse(msg, msg) else msg

  val msgs = TestAppender.messages.collect {
    case (l, m) if m.trim == expected => l
  }

  if (msgs.contains(level)) {
    // Pass
  } else if (msgs.isEmpty) {
    throw new RuntimeException("No test logged with content '" + msg + "' messages were:\n" + TestAppender.messages.reverse.mkString("\n"))
  } else {
    throw new RuntimeException("No test logged with content '" + msg + "' at level " + level + ", but some matched at level(s) " + msgs)
  }
}

TaskKey[Unit]("resetLogs") := {
  TestAppender.messages = Nil
  TestAppender.exceptions = Nil
  MockListener.gotAnEvent = false
}

TaskKey[Unit]("noErrors") := {
  val errors = TestAppender.messages.filter {
    case (l, m) => l == Level.Error || l == Level.Warn
  }
  if (!errors.isEmpty) {
    throw new RuntimeException("Expected no errors, but got:\n" + errors.reverse.mkString("\n"))
  }
  if (!TestAppender.exceptions.isEmpty) {
    throw new RuntimeException("Expected no exceptions, but got:\n" + TestAppender.exceptions.reverse.mkString("\n"))
  }
}

TaskKey[Unit]("mockListenerInvoked") := {
  if (!MockListener.gotAnEvent) {
    throw new RuntimeException("Mock test report listener was not invoked")
  }
}

ThisBuild / scalaVersion := {
  if (sbtVersion.value.startsWith("1.")) "2.12.21" else "3.9.0"
}
