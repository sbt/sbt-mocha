define(["bar"], function(bar) {
    return {
        callBar: function() {
            return "Called " + bar.call();
        }
    }
});