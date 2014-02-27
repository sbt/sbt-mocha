var assert = require("assert");

describe("Array", function() {
    describe("#indexOf()", function() {
        it("should return -1 when the value is not present", function() {
            assert.equal([1, 2, 3].indexOf(5), -1);
            assert.equal([1, 2, 3].indexOf(0), -1);
        });
        it("should return the position when the value is present", function() {
            assert.equal([1, 2, 3].indexOf(1), 0);
            assert.equal([1, 2, 3].indexOf(2), 1);
            assert.equal([1, 2, 3].indexOf(3), 2);
        });
    });
    describe("#push()", function() {
        it("should add an element to the end of the array", function() {
            var arr = [1, 2, 3];
            arr.push(4);
            assert.equal(arr.length, 3);
            assert.equal(arr[3], 4);
        });
    });
});