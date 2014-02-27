package com.typesafe.sbt.mocha

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWebPlugin._
import com.typesafe.sbt.web.incremental._
import sbt.File
import scala.Some
import com.typesafe.sbt.jse.SbtJsTaskPlugin
import com.typesafe.sbt.web.SbtWebPlugin
import spray.json._
import sbt.testing.Status
import com.typesafe.sbt.jse.SbtJsEnginePlugin.JsEngineKeys

/**
 * The sbt plugin plumbing around mocha.
 */
object SbtMochaPlugin extends SbtJsTaskPlugin {

  object MochaKeys {

    import KeyRanks._

    val mocha = TaskKey[(TestResult.Value, Map[String, SuiteResult])]("mocha", "Run mocha tests", BTask)

    val mochaRequires = SettingKey[Seq[String]]("mocha-requires", "Any scripts that should be required before running the tests", ASetting)
    val mochaGlobals = SettingKey[Seq[String]]("mocha-globals", "Global variables that should be shared between tests", ASetting)
    val mochaCheckLeaks = SettingKey[Boolean]("mocha-check-leaks", "Check for global variable leaks, defaults to false.", ASetting)
    val mochaBail = SettingKey[Boolean]("mocha-bail", "Bail after the first failure.  Defaults to false.", ASetting)

    val mochaOptions = TaskKey[MochaOptions]("mocha-options", "The mocha options.", CSetting)
    val mochaPrepareWorkDir = TaskKey[File]("mocha-prepare-work-dir", "Prepare the work dir with all the necessary files", CSetting)

    val mochaWorkDir = SettingKey[File]("mocha-work-dir", "The mocha work dir", CSetting)
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

  def mochaSettings = Seq(
    mochaRequires := Nil,
    mochaGlobals := Nil,
    mochaCheckLeaks := false,
    mochaBail := false,

    mochaOptions := {
      MochaOptions(mochaRequires.value, mochaGlobals.value, mochaCheckLeaks.value, mochaBail.value)
    },

    mochaWorkDir := target.value / "mocha",
    shellSource in mocha := {
      val f: File = (target in LocalRootProject).value / "mocha.js"
      val is = SbtMochaPlugin.getClass.getClassLoader.getResourceAsStream("com/typesafe/sbt/mocha/mocha.js")
      try {
        f.getParentFile.mkdirs()
        IO.transfer(is, f)
        f
      } finally {
        is.close()
      }
    },

    mochaPrepareWorkDir := {
      val workDir: File = mochaWorkDir.value

      // Note, copying all the resources won't be needed once the test assets target directory already has everything

      // Trigger dependencies to execute
      (copyResources in Assets).value
      (copyResources in TestAssets).value

      def createMappings(dirs: File*): Seq[(File, File)] = {
        for {
          dir: File <- dirs
          mapped <- (dir ** "*.js") x Path.rebase(dir, workDir)
        } yield mapped
      }

      val mappings = createMappings(
        (webJars in Assets).value,
        (webJars in TestAssets).value,
        (resourceManaged in Assets).value,
        (resourceManaged in TestAssets).value
      )

      val cache = streams.value.cacheDirectory / "mocha-tests"
      Sync(cache)(mappings)

      workDir
    },

    mocha := {
      val workDir: File = mochaPrepareWorkDir.value

      val modules = Seq(
        (nodeModules in Plugin).value.getCanonicalPath,
        (nodeModules in Assets).value.getCanonicalPath,
        (nodeModules in TestAssets).value.getCanonicalPath
      )

      val options: MochaOptions = mochaOptions.value

      val jsOptions = JsObject(Map(
        "requires" -> JsArray(options.requires.map { r =>
          JsString(new File(workDir, r).getCanonicalPath)
        }.toList),
        "globals" -> JsArray(options.globals.map(JsString.apply).toList),
        "checkLeaks" -> JsBoolean(options.checkLeaks),
        "bail" -> JsBoolean(options.bail)
      )).toString()

      // Now find just the test classes
      val testResourcesDir: File = (resourceManaged in TestAssets).value
      val testFilter: FileFilter = (jsFilter in TestAssets).value
      val tests = ((testResourcesDir ** testFilter) x Path.rebase(testResourcesDir, workDir)).map(_._2.getCanonicalPath)

      import scala.concurrent.duration._
      val results = executeJs(state.value, JsEngineKeys.engineType.value, modules, (shellSource in mocha).value,
        Seq(jsOptions, JsArray(tests.map(JsString.apply).toList).toString()), 100.days)

      val listeners = (testListeners in mocha).value

      results.headOption.map { jsResults =>
        new MochaTestReporting(workDir.getCanonicalPath + "/", listeners).logTestResults(jsResults)
      }.getOrElse((TestResult.Failed, Map.empty))
    },

    (executeTests in Test) <<= (executeTests in Test, mocha).map { (output, mochaResult) =>
      val (result, suiteResults) = mochaResult
      import TestResult._
      val overallResult = (output.overall, result) match {
        case (Error, _) | (_, Error) => Error
        case (Failed, _) | (_, Failed) => Failed
        case _ => Passed
      }
      Tests.Output(overallResult, output.events ++ suiteResults, output.summaries)
    },
    tags := Seq(Tags.Test -> 1)
  ) ++ Defaults.testTaskOptions(mocha)

}
