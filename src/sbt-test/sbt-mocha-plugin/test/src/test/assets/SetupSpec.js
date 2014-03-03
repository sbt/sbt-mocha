describe("setup", function() {
   it("should have been required", function() {
       require("assert").equal(myGlobal(), "is set");
   });
});