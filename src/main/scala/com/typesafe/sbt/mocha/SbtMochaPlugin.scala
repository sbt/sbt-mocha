package com.typesafe.sbt.mocha

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWebPlugin._
import com.typesafe.sbt.jse.SbtJsTaskPlugin
import com.typesafe.sbt.web.PathMapping
import spray.json._
import com.typesafe.sbt.jse.SbtJsEnginePlugin.JsEngineKeys
import sbinary.{Output, Input, Format, DefaultProtocol}
import sbinary.Operations._
import scala.Tuple2

/**
 * The sbt plugin plumbing around mocha.
 */
object SbtMochaPlugin extends SbtJsTaskPlugin {

  object MochaKeys {

    import KeyRanks._

    val mocha = TaskKey[(TestResult.Value, Map[String, SuiteResult])]("mocha", "Run mocha tests", BTask)
    val mochaOnly = InputKey[Unit]("mocha-only", "Executes the mocha tests provided as arguments or all tests if no arguments are provided.", ATask)

    val mochaTests = TaskKey[Seq[PathMapping]]("mocha-tests", "The tests that will be executed by mocha.", BTask)

    val mochaRequires = SettingKey[Seq[String]]("mocha-requires", "Any scripts that should be required before running the tests", ASetting)
    val mochaGlobals = SettingKey[Seq[String]]("mocha-globals", "Global variables that should be shared between tests", ASetting)
    val mochaCheckLeaks = SettingKey[Boolean]("mocha-check-leaks", "Check for global variable leaks, defaults to false.", ASetting)
    val mochaBail = SettingKey[Boolean]("mocha-bail", "Bail after the first failure.  Defaults to false.", ASetting)

    val mochaOptions = TaskKey[MochaOptions]("mocha-options", "The mocha options.", CSetting)
  }

  case class MochaOptions(
    requires: Seq[String],
    globals: Seq[String],
    checkLeaks: Boolean,
    bail: Boolean
  )

  import WebKeys._
  import SbtJsTaskPlugin.JsTaskKeys._
  import MochaKeys._

  def mochaSettings = inTask(mocha)(jsTaskSpecificUnscopedSettings) ++ Seq(
    mochaRequires := Nil,
    mochaGlobals := Nil,
    mochaCheckLeaks := false,
    mochaBail := false,

    mochaOptions := {
      MochaOptions(mochaRequires.value, mochaGlobals.value, mochaCheckLeaks.value, mochaBail.value)
    },

    shellFile in mocha := "com/typesafe/sbt/mocha/mocha.js",

    mochaTests := {
      val workDir: File = (assets in TestAssets).value
      val testFilter: FileFilter = (jsFilter in TestAssets).value
      (workDir ** testFilter).pair(relativeTo(workDir))
    },

    mocha := {
      val workDir: File = (assets in TestAssets).value

      val modules = Seq(
        (nodeModules in Plugin).value.getCanonicalPath,
        (nodeModules in Assets).value.getCanonicalPath,
        (nodeModules in TestAssets).value.getCanonicalPath
      )

      val options = mochaOptionsToJson(mochaOptions.value, workDir)

      val tests = mochaTests.value.map(_._1.getCanonicalPath)

      import scala.concurrent.duration._
      val results = executeJs(state.value, JsEngineKeys.engineType.value, modules, (shellSource in mocha).value,
        Seq(options, JsArray(tests.map(JsString.apply).toList).toString()), 100.days)

      val listeners = (testListeners in mocha).value

      results.headOption.map { jsResults =>
        new MochaTestReporting(workDir.getCanonicalPath + "/", listeners).logTestResults(jsResults)
      }.getOrElse((TestResult.Failed, Map.empty))
    },

    // Add the mocha task to execute tests
    (executeTests in Test) <<= (executeTests in Test, mocha).map { (output, mochaResult) =>
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

    tags in mocha := Seq(Tags.Test -> 1),

    mochaOnly := {

      val selected = Def.spaceDelimited("<tests>").parsed.toSet

      val availableTests: Seq[PathMapping] = mochaTests.value

      val tests = if (selected.isEmpty) {
        availableTests.map(_._1.getCanonicalPath)
      } else {
        availableTests.collect {
          case (file, n) if selected(n) || selected(n.replaceAll("\\.js$", "")) =>
            file.getCanonicalPath
        }
      }

      val workDir: File = (assets in TestAssets).value

      val modules = Seq(
        (nodeModules in Plugin).value.getCanonicalPath,
        (nodeModules in Assets).value.getCanonicalPath,
        (nodeModules in TestAssets).value.getCanonicalPath
      )

      val options = mochaOptionsToJson(mochaOptions.value, workDir)

      import scala.concurrent.duration._
      val results = executeJs(state.value, JsEngineKeys.engineType.value, modules, (shellSource in mocha).value,
        Seq(options, JsArray(tests.map(JsString.apply).toList).toString()), 100.days)

      val listeners = (testListeners in mocha).value

      val out = results.headOption.map { jsResults =>
        val (result, events) = new MochaTestReporting(workDir.getCanonicalPath + "/", listeners).logTestResults(jsResults)
        Tests.Output(result, events, Nil)
      }.getOrElse(Tests.Output(TestResult.Failed, Map.empty, Nil))

      Tests.showResults(streams.value.log, out, "No mocha tests found")
    }
  ) ++ Defaults.testTaskOptions(mocha)

  private def mochaOptionsToJson(options: MochaOptions, workDir: File): String = {
    JsObject(Map(
      "requires" -> JsArray(options.requires.map { r =>
        JsString(new File(workDir, r).getCanonicalPath)
      }.toList),
      "globals" -> JsArray(options.globals.map(JsString.apply).toList),
      "checkLeaks" -> JsBoolean(options.checkLeaks),
      "bail" -> JsBoolean(options.bail)
    )).toString()
  }
}
