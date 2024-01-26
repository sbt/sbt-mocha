package com.typesafe.sbt.mocha

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.{SbtWeb, PathMapping}
import spray.json._
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}

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

    val mochaOptions = TaskKey[MochaOptions]("mocha-options", "The mocha options.", CSetting)

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

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import autoImport._
  import MochaKeys._

  val testResultLogger = TestResultLogger.Default.copy(printNoTests = TestResultLogger.const(_ info "No mocha tests found"))

  override def buildSettings = inTask(mocha)(SbtJsTask.jsTaskSpecificUnscopedBuildSettings ++ Seq(
    moduleName := "mocha",
    shellFile := getClass.getResource("mocha.js")
  ))

  override def projectSettings = inConfig(Test)(Defaults.defaultTestTasks(mocha)) ++ inConfig(Test)(Defaults.defaultTestTasks(mochaOnly)) ++ inTask(mocha)(SbtJsTask.jsTaskSpecificUnscopedProjectSettings) ++ Seq(
    MochaKeys.requires := Nil,
    globals := Nil,
    checkLeaks := false,
    bail := false,

    mochaOptions := {
      MochaOptions(MochaKeys.requires.value, globals.value, checkLeaks.value, bail.value)
    },

    // Find the test files to run.  These need to be in the test assets target directory, however we only want to
    // find tests that originally came from the test sources directories (both managed and unmanaged).
    mochaTests := {
      val workDir: File = (TestAssets / assets).value
      val testFilter: FileFilter = (TestAssets / jsFilter).value
      val testSources: Seq[File] = (TestAssets / sources).value ++ (TestAssets /managedResources).value
      val testDirectories: Seq[File] = (TestAssets / sourceDirectories).value ++ (TestAssets /managedResourceDirectories).value
      (testSources ** testFilter).pair(Path.relativeTo(testDirectories)).map {
        case (_, path) => workDir / path -> path
      }
    },

    // Actually run the tests
    mochaExecuteTests := mochaTestTask.value(mochaTests.value.map(_._1)),

    // This ensures that mocha tests get executed when test is run
    (Test / executeTests) := {
      val output = (Test / executeTests).value
      val mochaResult = mochaExecuteTests.value
      val (result, suiteResults) = mochaResult
      import TestResult._

      // Merge the mocha result with the overall result of the rest of the tests
      val overallResult = (output.overall, result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      Tests.Output(overallResult, output.events ++ suiteResults, output.summaries)
    },

    // Defaults.defaultTestTasks(...) above sets logBuffered to true, but we don't want that for these tasks
    Test / mocha / logBuffered := false,
    Test / mochaOnly / logBuffered := false,

    // For running mocha tests in isolation from other types of tests
    mocha := {
      val (result, events) = mochaExecuteTests.value
      testResultLogger.run(streams.value.log, Tests.Output(result, events, Nil), "")
    },
  
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

      // Run them
      val (result, events) = mochaTestTask.value(tests)
      testResultLogger.run(streams.value.log, Tests.Output(result, events, Nil), "")
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
      (Plugin / nodeModules).value
      (Assets / nodeModules).value
      (TestAssets / nodeModules).value

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
      )).toString()

      val stateValue = state.value
      val engineTypeValue = (mocha / engineType).value
      val commandValue = (mocha / command).value
      val shellSourceValue = (mocha / shellSource).value

      val listeners = (Test / mocha / testListeners).value

      { (tests: Seq[File]) =>
        import scala.concurrent.duration._
        val results = SbtJsTask.executeJs(stateValue, engineTypeValue, commandValue, modules, shellSourceValue,
          Seq(jsOptions, JsArray(tests.map(t => JsString.apply(t.getCanonicalPath)).toVector).toString()))

        results.headOption.map { jsResults =>
          new MochaTestReporting(workDir, listeners).logTestResults(jsResults)
        }.getOrElse((TestResult.Failed, Map.empty))
      }
    }
  }
}
