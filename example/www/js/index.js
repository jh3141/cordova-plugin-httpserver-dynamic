
var app = {
    // Application Constructor
    initialize: function() {
        this.logPanel = document.getElementById ('log');
        this.log ('Waiting for deviceready event');
        document.addEventListener('deviceready', this.onDeviceReady.bind(this), false);
    },

    onDeviceReady: function() {
        var button = document.getElementById('startServer');
        button.addEventListener('click', this.startServer.bind(this), false);
        button.disabled = false;
        this.counter = 1;
        this.log ('Application initialized.  Press the button to start the server.');
    },

    startServer: function () {
        cordova.plugins.dynamicHttpServer.startServer (
            document.getElementById('host').value,
            document.getElementById('port').value/1,
            this.handleRequest.bind(this),
            this.serverStarted.bind(this),
            this.handleError.bind(this));
    },

    handleRequest: function (hostname, port, request, sendResponse) {
        var num = this.counter++;
        this.log ('Got request ' + num + ' on ' + hostname + ":" + port + ": " + JSON.stringify(request));
        sendResponse (200, "text/plain", { "X-Request-Counter": num },
            "This is the response to request " + num,
            function () { this.log ('Response ' + num + ' sent'); },
            this.handleError.bind(this));
    },

    serverStarted: function (hostname, port, serverId) {
        this.log ('Server started at ' + hostname + ':' + port + " with ID " + serverId);
        document.getElementById('startServerPanel').style.display = "none";
    },

    handleError: function (error) {
        this.log ('Error: ' + error);
    },

    log: function (message) {
        this.logPanel.appendChild (document.createTextNode(message));
        this.logPanel.appendChild (document.createElement ('br'));
    }
};

app.initialize();
