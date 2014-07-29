describe("a test asset", function() {

  it("should be able to require a main asset", function() {
    require("./MainAsset").foo();
  });

  it("should be able to require a common asset", function() {
    require("./lib/common/CommonAsset").foo();
  });

  it("should be able to require a common test asset", function() {
    require("./lib/common/CommonTestAsset").foo();
  });

});
