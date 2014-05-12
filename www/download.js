var exec = require("cordova/exec");

var Download = function () {
    this.name = "Download";
};

Download.prototype.start = function (url, func) {
	if (!url) {
        return;
    }

	exec(func, null, "Download", "start", [url]);
};

Download.prototype.progress = function (func) {
	exec(func, null, "Download", "progress", []);
};

Download.prototype.stop = function (func) {
	exec(func, null, "Download", "stop", []);
};

module.exports = new Download();

