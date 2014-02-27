var assert = require("assert");

describe("This fine and lovely spec", function() {
    it("should be able to pass", function() {
        assert.equal("foo", "foo");
    });
    it("should throw a plain exception", function() {
       thisWillBeAError();
    });
});