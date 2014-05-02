package com.typesafe.sbt.mocha

import spray.json._
import sbt._
import sbt.testing._

/**
 * Handles reporting test results
 */
private [mocha] class MochaTestReporting(mochaWorkDir: File, listeners: Seq[TestReportListener]) {

  val testsListeners = listeners collect { case tl: TestsListener => tl }
  testsListeners.foreach(_.doInit())

  val mochaWorkPath = mochaWorkDir.getCanonicalPath

  private def sanitiseFilename(name: String) = {
    name.stripPrefix(mochaWorkPath).drop(1)
  }

  private def overallResultFromSuiteResults(suiteResults: Iterable[SuiteResult]) = {
    import sbt.TestResult._
    suiteResults.foldLeft(Passed) { (a, b) =>
      (a, b.result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
    }
  }

  private def mergeSuiteResults(a: SuiteResult, b: SuiteResult) = {
    import TestResult._
    val overallResult = (a.result, b.result) match {
      case (Error, _) | (_, Error) => Error
      case (Failed, _) | (_, Failed) => Failed
      case _ => Passed
    }
    new SuiteResult(overallResult,
      a.passedCount + b.passedCount,
      a.failureCount + b.failureCount,
      a.errorCount + b.errorCount,
      a.skippedCount + b.skippedCount,
      a.ignoredCount + b.ignoredCount,
      a.canceledCount + b.canceledCount,
      a.pendingCount + b.pendingCount
    )
  }

  def logTestResults(results: JsValue): (TestResult.Value, Map[String, SuiteResult]) = {
    import MochaJsonProtocol._

    val suite = results.convertTo[MochaSuite]

    // The mocha suite is a recursive structure, each suite may have many sub suites
    // Each suite may define a filename, we will call any suite that defines a filename a top level suite

    // First, find all the top level suites (ie, suites that define a filename)
    def splitTopLevel(suite: MochaSuite): (Seq[MochaSuite], Option[MochaSuite]) = {
      val splitSuites = suite.suites.map(splitTopLevel)
      val nonTopLevel = splitSuites.collect {
        case (_, Some(s)) => s
      }
      val stripped = suite.copy(suites = nonTopLevel)
      val topLevel = splitSuites.flatMap(_._1)

      if (suite.filename.isDefined) {
        Seq(stripped) ++ topLevel -> None
      } else {
        topLevel -> Some(stripped)
      }
    }

    val (topLevel, orphans) = splitTopLevel(suite)

    val suiteResults = topLevel.map { suite =>
      // All top level suites should have a file name (otherwise they wouldn't have been detected as top level)
      val filename = sanitiseFilename(suite.filename.getOrElse("no filename?"))
      filename -> handleSuite(suite, filename, 0)
    }

    val allSuiteResults = orphans.filterNot(_.suites.isEmpty).fold(suiteResults)(orphan =>
      suiteResults :+ ("mocha tests" -> handleSuite(orphan, "mocha tests", 0)))

    // Merge all suite results into a map
    val suiteResultsMap = allSuiteResults.groupBy(_._1).map {
      case (name, rs) => name -> rs.map(_._2).reduce(mergeSuiteResults)
    }.toMap

    val overallResult = overallResultFromSuiteResults(suiteResultsMap.values)
    testsListeners.foreach(_.doComplete(overallResult))
    (overallResult, suiteResultsMap)
  }

  /**
   * Handle a suite object
   *
   * @param suite The suite to handle
   * @param filename The filename
   * @param indent The current indent
   * @return
   */
  def handleSuite(suite: MochaSuite, filename: String, indent: Int): SuiteResult = {

    // The crazy thing here is that the JUnitXmlTestsListener interprets the name of the
    // group as a filename. Thus we have a function here to make a sensible filename that
    // can be passed into startGroup and endGroup. Issue reported here:
    // https://github.com/sbt/sbt/issues/1306
    val suiteFileTitle = (for {
      filename <- suite.filename
      title <- suite.title
    } yield (sanitiseFilename(filename) + "-" + title).replaceAll("\\W+", "")).getOrElse("unknown")

    val suiteTitle = suite.title.filterNot(_.isEmpty)

    // Log/fire events for start of suite
    suiteTitle.foreach { title =>
      listeners.foreach { listener =>
        listener.startGroup(suiteFileTitle)
        listener.contentLogger(
          new TestDefinition(filename, MochaFingerprint, false, Array(new NestedSuiteSelector(title)))
        ).foreach { logger =>
          logger.log.info(new String(Array.fill(indent)(' ')) + title)
        }
      }
      title
    }

    // Only indent if there was a title
    val nextIndent = suiteTitle.fold(indent)(_ => indent + 2)

    def selector(name: String) = suiteTitle match {
      case Some(t) => new NestedTestSelector(t, name)
      case None => new TestSelector(name)
    }

    // Convert all the test results to SBT results
    val results = suite.tests.map { test =>
      val duration = test.duration.getOrElse(0l)
      val sel = selector(test.title)
      test.status match {
        case "pass" =>
          new MochaTestEvent(test.title, Status.Success, sel, None, duration)
        case "pending" =>
          new MochaTestEvent(test.title, Status.Pending, sel, None, duration)
        case "fail" =>
          new MochaTestEvent(test.title, Status.Failure, sel, test.error.map(createThrowableFromMochaError),
          duration)
        case "error" =>
          new MochaTestEvent(test.title, Status.Error, sel, test.error.map(createThrowableFromMochaError),
            duration)
      }
    }

    // Raise a test event
    listeners.foreach(_.testEvent(TestEvent(results)))

    val indentStr = new String(Array.fill(nextIndent)(' '))
    // Actually log the test output
    results.foreach { result =>
      val testDefinition = new TestDefinition(filename, result.fingerprint(), false,
        Array(result.selector))
      listeners.foreach { listener =>
        listener.contentLogger(testDefinition).foreach { logger =>
          def status(color: String, s: String) = {
            if (logger.log.ansiCodesSupported()) {
              color + s + scala.Console.RESET
            } else {
              s
            }
          }

          result.status match {
            case Status.Success =>
              logger.log.info(indentStr + status(scala.Console.GREEN, "+") + " " + result.fullyQualifiedName)
            case Status.Pending =>
              logger.log.info(indentStr + status(scala.Console.YELLOW, "o") + " " + result.fullyQualifiedName)
            case Status.Failure =>
              logger.log.info(indentStr + status(scala.Console.YELLOW, "x") + " " + result.fullyQualifiedName)
              if (result.throwable.isDefined) {
                logger.log.error(result.throwable.get().toString)
                logger.log.error(result.throwable.get().getStackTrace.map("    at " + _).mkString("\n"))
              }
            case Status.Error =>
              logger.log.info(indentStr + status(scala.Console.RED, "!") + " " + result.fullyQualifiedName)
              if (result.throwable.isDefined) {
                logger.log.error(result.throwable.get().toString)
                logger.log.error(result.throwable.get().getStackTrace.map("    at " + _).mkString("\n"))
              }
            case _ => logger.log.info(indentStr + status(scala.Console.RED, "x") + " " + result.fullyQualifiedName)
          }
        }
      }
    }

    // sub suites
    val overallResult = suite.suites.map { subSuite =>
      handleSuite(subSuite, filename, nextIndent)
    }.fold(SuiteResult(results)) { (a, b) =>
      import TestResult._
      val overallResult = (a.result, b.result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      new SuiteResult(overallResult,
        a.passedCount + b.passedCount,
        a.failureCount + b.failureCount,
        a.errorCount + b.errorCount,
        a.skippedCount + b.skippedCount,
        a.ignoredCount + b.ignoredCount,
        a.canceledCount + b.canceledCount,
        a.pendingCount + b.pendingCount
      )
    }

    // Handle end of suite
    suiteTitle.foreach { title =>
      listeners.foreach { listener =>
        listener.endGroup(suiteFileTitle, overallResult.result)
      }
    }

    overallResult
  }

  class MochaTestEvent(val fullyQualifiedName: String,
                               val status: Status,
                               val selector: Selector,
                               exception: Option[Throwable],
                               val duration: Long) extends sbt.testing.Event {

    lazy val throwable = exception match {
      case Some(t) => new OptionalThrowable(t)
      case None => new OptionalThrowable()
    }

    def fingerprint() = MochaFingerprint
  }

  def createThrowableFromMochaError(error: MochaError): Throwable = {
    new MochaTestError(error.name, error.message.orNull, parseStackTrace(error.stack.getOrElse("")))
  }

  val NodeStackTraceRegex = """\s*at\s+([^\s]*)\s+\((.*):(\d+):\d+\)""".r

  def parseStackTrace(stack: String) = {
    stack.split("\n") collect {
      case NodeStackTraceRegex(classAndMethod, filename, line) =>
        val lastDot = classAndMethod.lastIndexOf('.')
        val sanitised = sanitiseFilename(filename)
        if (lastDot != -1) {
          val (clazz, method) = classAndMethod.splitAt(lastDot)
          new StackTraceElement(clazz, method.drop(1), sanitised, line.toInt)
        } else {
          new StackTraceElement("", classAndMethod, sanitised, line.toInt)
        }
    }
  }

  class MochaTestError(name: String, msg: String, stackTrace: Array[StackTraceElement]) extends Exception(msg) {
    setStackTrace(stackTrace)
    // Filling in the stack trace is unnecessary since we are supplying our own
    override def fillInStackTrace() = this
    override def toString = if (msg != null) {
      name + ": " + msg
    } else {
      name
    }
  }

  object MochaFingerprint extends Fingerprint

  case class MochaSuite(filename: Option[String], title: Option[String], suites: Seq[MochaSuite], tests: Seq[MochaTest])
  case class MochaTest(title: String, status: String, duration: Option[Long], error: Option[MochaError])
  case class MochaError(name: String, message: Option[String], stack: Option[String])

  object MochaJsonProtocol extends DefaultJsonProtocol {
    implicit val mochaErrorFormat: JsonFormat[MochaError] = jsonFormat3(MochaError)
    implicit val mochaTestFormat: JsonFormat[MochaTest] = jsonFormat4(MochaTest)
    implicit val mochaSuiteFormat: JsonFormat[MochaSuite] = lazyFormat(jsonFormat4(MochaSuite))
  }


}