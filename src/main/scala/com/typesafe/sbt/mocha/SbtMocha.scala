package com.typesafe.sbt.mocha

import com.typesafe.sbt.PluginCompat.*
import sbt.*
import sbt.Keys.*
import com.typesafe.sbt.web.{PathMapping, SbtWeb}
import spray.json.*
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}
import sbt.mocha.BadCitizen.*
import xsbti.FileConverter

object Import {
  object MochaKeys {
    import KeyRanks._

    val mocha = TaskKey[Unit]("mocha", "Run all mocha tests.", BTask)
    val mochaOnly = InputKey[Unit]("mocha-only", "Execute the mocha tests provided as arguments or all tests if no arguments are provided.", ATask)

    val mochaExecuteTests = TaskKey[(TestResult, Map[String, SuiteResult])]("mocha-execute-tests", "Execute all mocha tests and return the result.", CTask)
    val mochaTests = TaskKey[Seq[PathMapping]]("mocha-tests", "The tests that will be executed by mocha.", CTask)

    val requires = SettingKey[Seq[String]]("mocha-requires", "Any scripts that should be required before running the tests", ASetting)
    val globals = SettingKey[Seq[String]]("mocha-globals", "Global variables that should be shared between tests", ASetting)
    val checkLeaks = SettingKey[Boolean]("mocha-check-leaks", "Check for global variable leaks, defaults to false.", ASetting)
    val bail = SettingKey[Boolean]("mocha-bail", "Bail after the first failure.  Defaults to false.", ASetting)

    val mochaOptions = uncached(TaskKey[MochaOptions]("mocha-options", "The mocha options.", CSetting))

    case class MochaOptions(
                             requires: Seq[String],
                             globals: Seq[String],
                             checkLeaks: Boolean,
                             bail: Boolean
                             )
  }
}

/**
 * The sbt plugin plumbing around mocha.
 */
object SbtMocha extends AutoPlugin {

  override def requires = SbtJsTask && sbt.plugins.JvmPlugin

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import autoImport._
  import MochaKeys._

  val testResultLogger = TestResultLogger.Default.copy(printNoTests = TestResultLogger.const(_.info("No mocha tests found")))

  private def reportResults(log: Logger, results: Tests.Output, taskName: String): Unit = {
    testResultLogger.run(log, results, taskName)
    // sbt 2.1's result logger only logs; the task must propagate test failures.
    if (results.overall == TestResult.Failed || results.overall == TestResult.Error) {
      sys.error("Mocha tests failed")
    }
  }

  override def buildSettings = Project.inTask(mocha)(SbtJsTask.jsTaskSpecificUnscopedBuildSettings ++ Seq(
    moduleName := "mocha",
    shellFile := getClass.getResource("mocha.js")
  ))

  override def projectSettings = inConfig(Test)(Seq(
    mocha / logBuffered := false,
    mochaOnly / logBuffered := false,
    mocha / tags := Seq.empty,
    mochaOnly / tags := Seq.empty,
    executeTests := uncached(Def.task {
      val executionOutput = executeTests.value
      val mochaResult = mochaExecuteTests.value
      val (result, suiteResults) = mochaResult
      import TestResult._

      // Merge the mocha result with the overall result of the rest of the tests
      val overallResult = (executionOutput.overall, result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      output(overallResult, executionOutput.events ++ suiteResults, executionOutput.summaries)
    }).value,
    test := uncached(Def.task {
      val results = executeTests.value
      val mochaResult = mochaExecuteTests.value
      val (result, suiteResults) = mochaResult
      import TestResult._

      val overallResult = (results.overall, result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      val combinedResults = output(overallResult, results.events ++ suiteResults, results.summaries)
      reportResults(streams.value.log, combinedResults, "test")
      combinedResults.overall
    }).value
  )) ++ Project.inTask(mocha)(SbtJsTask.jsTaskSpecificUnscopedProjectSettings) ++ Seq(
    MochaKeys.requires := Nil,
    globals := Nil,
    checkLeaks := false,
    bail := false,

    mochaOptions := uncached(Def.task {
      MochaOptions(MochaKeys.requires.value, globals.value, checkLeaks.value, bail.value)
    }).value,

    mochaTests := uncached(Def.task {
      implicit val fc: FileConverter = fileConverter.value
      val workDir: File = (TestAssets / assets).value
      val testFilter: FileFilter = (TestAssets / jsFilter).value
      val testSources: Seq[File] = (TestAssets / sources).value ++ (TestAssets /managedResources).value
      val testDirectories: Seq[File] = (TestAssets / sourceDirectories).value ++ (TestAssets /managedResourceDirectories).value
      val pairs = (testSources ** testFilter).pair(Path.relativeTo(testDirectories))
      pairs.map {
        case (_, path) => toFileRef(workDir / path) -> path
      }
    }).value,

    mochaExecuteTests := uncached (Def.task[(TestResult,Map[String,SuiteResult])] {
      implicit val fc: FileConverter = fileConverter.value
      mochaTestTask.value(mochaTests.value.map( pathMap => toFile(pathMap._1)))
    }).value,

    // For running mocha tests in isolation from other types of tests
    mocha := uncached(Def.task {
      val (result, events) = mochaExecuteTests.value
      reportResults(streams.value.log, output(result, events, Nil), "")
    }).value,
  
    // For running only a specified set of tests
    mochaOnly := {
      // Parse the tests
      val selected = Def.spaceDelimited("<tests>").parsed.toSet
      val availableTests: Seq[PathMapping] = mochaTests.value

      // Select the correct tests to run
      val tests = if (selected.isEmpty) {
        availableTests.map(_._1)
      } else {
        availableTests.collect {
          case (file, n) if selected(n) || selected(n.replaceAll("\\.js$", "")) =>
            file
        }
      }

      implicit val fc: FileConverter = fileConverter.value
      // Run them
      val (result, events) = mochaTestTask.value(tests.map(toFile))
      reportResults(streams.value.log, output(result, events, Nil), "")
    }
  ) ++ inConfig(Test)(Defaults.testTaskOptions(mocha)) ++ inConfig(Test)(Defaults.testTaskOptions(mochaOnly))

  /**
   * This is a task that produces a function that will take the test files to run, and then run it.
   * 
   * The purpose for this is to allow easily factoring out all the common code from mocha and mocha-only, while still
   * taking advantage of the SBT macros.  Since the tests to be run can't be determined in a task in the case of
   * mocha-only, since they come from the command line input of that particular run, a function is the most convenient
   * way to do this.
   */
  private val mochaTestTask: Def.Initialize[Task[Seq[File] => (TestResult, Map[String, SuiteResult])]] = Def.task {
    {
      val workDir: File = (TestAssets / assets).value

      // One way of declaring dependencies
      val plugin = (Plugin / nodeModules).value
      val assert = (Assets / nodeModules).value
      val testassert = (TestAssets / nodeModules).value

      val modules = (
        (Plugin / nodeModuleDirectories).value ++
          (Assets / nodeModuleDirectories).value ++
          (TestAssets / nodeModuleDirectories).value
        ).map(_.getCanonicalPath)

      val options = mochaOptions.value

      val jsOptions = JsObject(Map(
        "requires" -> JsArray(options.requires.map { r =>
          JsString(new File(workDir, r).getCanonicalPath)
        }.toVector),
        "globals" -> JsArray(options.globals.map(JsString.apply).toVector),
        "checkLeaks" -> JsBoolean(options.checkLeaks),
        "bail" -> JsBoolean(options.bail)
      )).compactPrint

      val stateValue = state.value
      val engineTypeValue = (mocha / engineType).value
      val commandValue = (mocha / command).value
      val shellSourceValue = (mocha / shellSource).value

      val listeners = (Test / mocha / testListeners).value

      { (tests: Seq[File]) =>
        import scala.concurrent.duration._
        val results = SbtJsTask.executeJs(stateValue, engineTypeValue, commandValue, modules, shellSourceValue,
          Seq(jsOptions, JsArray(tests.map(t => JsString.apply(t.getCanonicalPath)).toVector).compactPrint))

        results.headOption.map { jsResults =>
          new MochaTestReporting(workDir, listeners).logTestResults(jsResults)
        }.getOrElse((TestResult.Failed, Map.empty))
      }
    }
  }
}
