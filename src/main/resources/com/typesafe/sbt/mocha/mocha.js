var requireIfExists = require('node-require-fallback');

var Mocha = requireIfExists("mocha/10.2.0", "mocha"); // sync with build.sbt

var mocha = new Mocha();

var args = process.argv;

var options = JSON.parse(args[2]);
var tests = JSON.parse(args[3]);

function MyReporter(runner) {

    var currentSuite = [];

    runner.on("suite", function(suite) {
        if (suite.root) {
            currentSuite.unshift({
                title: suite.title,
                suites: [],
                tests: []
            });
        } else {
            var s = {
                title: suite.title,
                filename: suite.filename,
                suites: [],
                tests: []
            };
            currentSuite[0].suites.push(s);
            currentSuite.unshift(s);
        }

    });

    runner.on("suite end", function(suite) {
        if (!suite.root) {
            currentSuite.shift();
        }
    });

    runner.on("pass", function(test) {
        currentSuite[0].tests.push({
            title: test.title,
            status: "pass",
            duration: test.duration
        });
    });

    runner.on("fail", function(test, err) {

        // This seems to be a convention in testing in node
        if (err.name == "AssertionError") {
            currentSuite[0].tests.push({
                title: test.title,
                status: "fail",
                duration: test.duration,
                error: {
                    name: err.name,
                    message: (err.message ? err.message + ": " : "") +
                        "Got value " + JSON.stringify(err.actual) + " but expected a value " + err.operator + " " + JSON.stringify(err.expected),
                    stack: err.stack
                }
            })
        } else {
            currentSuite[0].tests.push({
                title: test.title,
                status: "error",
                duration: test.duration,
                error: {
                    name: err.name,
                    message: err.message,
                    stack: err.stack
                }
            })
        }
    });

    runner.on("pending", function(test) {
        currentSuite[0].tests.push({
            title: test.title,
            status: "pending",
            duration: test.duration
        });
    });

    runner.on("end", function() {
        if (!currentSuite.length) {
            currentSuite.push({
                suites: [],
                tests: []
            });
        }
        console.log("\u0010", JSON.stringify(currentSuite[0]));
    });
}

mocha.reporter(MyReporter);

// This works around the issue that mocha doesn't put the filename on the test suite
mocha.suite.on("post-require", function(ctx, file) {
    // Add the filename to any first level suites that don't have a filename defined
    mocha.suite.suites.forEach(function(suite) {
       if (suite.filename === undefined) {
           suite.filename = file;
       }
    });
});

if (options.requires) {
    options.requires.forEach(function(r) {
        require(r);
    });
}

if (options.globals) {
    mocha.globals(options.globals);
}

if (options.bail) {
    mocha.bail();
}

if (options.checkLeaks) {
    mocha.checkLeaks();
}

mocha.files = tests;
mocha.run();
