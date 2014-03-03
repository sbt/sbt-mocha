describe("Foo", function() {
    it("should call bar", function(done) {
        new Squire().mock("bar", {
            call: function() {
                return "mock bar"
            }
        }).require(["foo/Foo"], function(foo) {
            assert.equal(foo.callBar(), "Called mock bar");
            done();
        });
    });
});