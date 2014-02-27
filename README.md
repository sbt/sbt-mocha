sbt-jshint-plugin
=================

Allows JSHint to be used from within sbt. Builds on com.typesafe:js-engine in order to execute jshint.js
along with the scripts to verify. js-engine enables high performance linting given parallelism and native
JS engine execution.

To use this plugin use the addSbtPlugin command within your project's plugins.sbt (or as a global setting) i.e.:

    resolvers ++= Seq(
        Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
        Resolver.sonatypeRepo("snapshots"),
        "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
        "Spray Releases" at "http://repo.spray.io/"
        )

    addSbtPlugin("com.typesafe.sbt" % "sbt-jshint-plugin" % "1.0.0-SNAPSHOT")

Then declare the settings required in your build file (JSHintPlugin depends on some other, more generalised settings
to be defined). For example, for build.sbt:

    import com.typesafe.sbt.web.WebPlugin
    import com.typesafe.sbt.jse.JsEnginePlugin
    import com.typesafe.sbt.jshint.JSHintPlugin

    WebPlugin.webSettings

    JsEnginePlugin.jsEngineSettings

    JSHintPlugin.jshintSettings

By default linting occurs as part of your project's `test` task. Both src/main/assets/\*\*/\*.js and
src/test/assets/\*\*/\*.js sources are linted.

Options can be specified in accordance with the
[JSHint website](http://www.jshint.com/docs) and they share the same set of defaults. To set an option you can
provide a `.jshintrc` file within your project's base directory. If there is no such file then a `.jshintrc` file will
be search for in your home directory. This behaviour can be overridden by using a `JSHintPlugin.config` setting for the plugin.
`JSHintPlugin.config` is used to specify the location of a configuration file.

&copy; Typesafe Inc., 2013, 2014