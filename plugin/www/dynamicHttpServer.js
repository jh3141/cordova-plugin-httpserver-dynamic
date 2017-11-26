var exec = require('cordova/exec');

exports.startServer = function (hostname, port, callback, onStarted, error) {
    exec(success, error, 'cordova-plugin-httpserver-dynamic', 'startServer', [hostname, port]);

    function success (result)
    {
        if (result.serverStarted)
            onStarted (hostname, port, result.serverId);
        else if (result.requestId)
            callback (hostname, port, {
                method: result.requestMethod,
                path: result.requestPath,
                parameters: result.requestParameters,
                cookies: result.requestCookies,
                headers: result.requestHeaders,
                files: result.requestFiles
            }, sendResponse);
        else
            console.warn ("Unrecognised callback invocation from native http server", result);

        function sendResponse (status, contentType, headers, data, success, error)
        {
            exec (success, error, 'cordova-plugin-httpserver-dynamic', 'sendResponse', [result.serverId, result.requestId, {
                status: status,
                contentType: contentType,
                headers: headers,
                data: data
            }]);
        }
    }
};

#exports.stopServer = function (serverId, success, error) {
#    exec(success, error, 'cordova-plugin-httpserver-dynamic', 'stopServer', [port]);
#}
