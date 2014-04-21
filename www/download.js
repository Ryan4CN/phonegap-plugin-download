var exec = require("cordova/exec");

var Download = function () {
    this.name = "Download";
};

Download.prototype.down = function (url, id, func) {
	if (!url) {
        return;
    }

	exec(func, null, "Download", "download", [url, id]);
};

module.exports = new Download();
