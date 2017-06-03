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

    val mochaExecuteTests = TaskKey[(TestResult.Value, Map[String, SuiteResult])]("mocha-execute-tests", "Execute all mocha tests and return the result.", CTask)
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

  override def projectSettings = inTask(mocha)(SbtJsTask.jsTaskSpecificUnscopedSettings) ++ Seq(
    MochaKeys.requires := Nil,
    globals := Nil,
    checkLeaks := false,
    bail := false,

    mochaOptions := {
      MochaOptions(MochaKeys.requires.value, globals.value, checkLeaks.value, bail.value)
    },

    shellFile in mocha := getClass.getResource("mocha.js"),

    // Find the test files to run.  These need to be in the test assets target directory, however we only want to
    // find tests that originally came from the test sources directories (both managed and unmanaged).
    mochaTests := {
      val workDir: File = (assets in TestAssets).value
      val testFilter: FileFilter = (jsFilter in TestAssets).value
      val testSources: Seq[File] = (sources in TestAssets).value ++ (managedResources in TestAssets).value
      val testDirectories: Seq[File] = (sourceDirectories in TestAssets).value ++ (managedResourceDirectories in TestAssets).value
      (testSources ** testFilter).pair(relativeTo(testDirectories)).map {
        case (_, path) => workDir / path -> path
      }
    },

    // Actually run the tests
    mochaExecuteTests := mochaTestTask.value(mochaTests.value.map(_._1)),

    // This ensures that mocha tests get executed when test is run
    (executeTests in Test) := {
      val output = (executeTests in Test).value
      val (result, suiteResults) = mochaExecuteTests.value
      import TestResult._

      // Merge the mocha result with the overall result of the rest of the tests
      val overallResult = (output.overall, result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      Tests.Output(overallResult, output.events ++ suiteResults, output.summaries)
    },

    // For running mocha tests in isolation from other types of tests
    mocha := {
      val (result, events) = mochaExecuteTests.value
      testResultLogger.run(streams.value.log, Tests.Output(result, events, Nil), "")
    },

    tags in mocha := Seq(Tags.Test -> 1),
  
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
  ) ++ Defaults.testTaskOptions(mocha)

  /**
   * This is a task that produces a function that will take the test files to run, and then run it.
   * 
   * The purpose for this is to allow easily factoring out all the common code from mocha and mocha-only, while still
   * taking advantage of the SBT macros.  Since the tests to be run can't be determined in a task in the case of
   * mocha-only, since they come from the command line input of that particular run, a function is the most convenient
   * way to do this.
   */
  private val mochaTestTask: Def.Initialize[Task[Seq[File] => (TestResult.Value, Map[String, SuiteResult])]] = Def.task {
    { (tests: Seq[File]) =>

      val workDir: File = (assets in TestAssets).value

      // One way of declaring dependencies
      (nodeModules in Plugin).value
      (nodeModules in Assets).value
      (nodeModules in TestAssets).value

      val modules = (
        (nodeModuleDirectories in Plugin).value ++
          (nodeModuleDirectories in Assets).value ++
          (nodeModuleDirectories in TestAssets).value
        ).map(_.getCanonicalPath)

      val options = mochaOptions.value
    
      val jsOptions = JsObject(Map(
        "requires" -> JsArray(options.requires.map { r =>
          JsString(new File(workDir, r).getCanonicalPath)
        }: _*),
        "globals" -> JsArray(options.globals.map(JsString.apply): _*),
        "checkLeaks" -> JsBoolean(options.checkLeaks),
        "bail" -> JsBoolean(options.bail)
      )).toString()

      import scala.concurrent.duration._
      val results = SbtJsTask.executeJs(state.value, (engineType in mocha).value, (command in mocha).value, modules, (shellSource in mocha).value,
        Seq(jsOptions, JsArray(tests.map(t => JsString.apply(t.getCanonicalPath)): _*).toString()), 100.days)

      val listeners = (testListeners in (Test, mocha)).value

      results.headOption.map { jsResults =>
        new MochaTestReporting(workDir, listeners).logTestResults(jsResults)
      }.getOrElse((TestResult.Failed, Map.empty))
    }
  }
}
