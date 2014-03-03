sbt-mocha-plugin
=================

Allows mocha to be used from within sbt.

To use this plugin use the addSbtPlugin command within your project's plugins.sbt (or as a global setting) i.e.:

    resolvers ++= Seq(
        Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
        Resolver.sonatypeRepo("snapshots"),
        "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
        "Spray Releases" at "http://repo.spray.io/"
        )

    addSbtPlugin("com.typesafe.sbt" % "sbt-jshint-plugin" % "1.0.0-SNAPSHOT")

Then declare the settings required in your build file (mocha depends on some other, more generalised settings
to be defined). For example, for build.sbt:

    import com.typesafe.sbt.jse.SbtJsTaskPlugin._

    webSettings

    jsEngineAndTaskSettings

    mochaSettings

By default, any tests matching either `*Test.js` or `*Spec.js` are tested.  This can be overridden by defining a different includes, for example:

```scala
jsFilter in TestAssets := GlobFilter("Test*.js")
```

Tests are read from `src/test/assets` and `src/test/public`.  For example, you can create `src/test/assets/FooSpec`:

```js
var assert = require("assert");
describe("Foo", function() {
  it("say hello", function() {
    var foo = require("./Foo");
    assert.equal(foo.hello("world"), "hello world");
  });
});
```

All assets are copied to a working directory, which means any test or main assets may be imported via relative paths from that working directory.

Any node WebJars are made available as normal node modules via the `require` method, and all other WebJars are installed in the `lib` directory under the WebJars name.

The following options are supported:

* `MochaKeys.requires` - A list of resources that should be required before each test run.
* `MochaKeys.globals` - A list of variables that Mocha should make global.
* `MochaKeys.checkLeaks` - Set to true to run mocha in check leaks mode.
* `MochaKeys.bail` - Set to true to tell mocha to bail after the first run.

&copy; Typesafe Inc., 2013, 2014
