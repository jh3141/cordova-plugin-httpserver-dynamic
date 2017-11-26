# cordova-plugin-httpserver-dynamic

A plugin for Apache Cordova that runs a web server that is able to serve dynamic content.
Android support available now, iOS to come soon.

# Installation

Installation is most easily carried out using the `cordova-cli` package and the
published NPM module:

```
$ cordova plugin add cordova-plugin-httpserver-dynamic
```

Alternatively, if you want to track changes directly from the git repository, use
a github URL as the source:

```
$ cordova plugin add https://github.com/jh3141/cordova-plugin-httpserver-dynamic:plugin
```

After installation, it's probably a good idea to save your plugin configuration to
`config.xml`:

```
$ cordova plugin save
```

# Usage

The plugin's Javascript module loads at `cordova.plugins.dynamicHttpServer`.  This
module contains only one useful function:

```
cordova.plugins.dynamicHttpServer.startServer (hostname, port, callback, onStarted, error)
```
The parameters are as follows:

* `hostname : string` - the host name or address (either dotted quad or IPv6 format) of the address to
  bind the server socket to.  May be either null or the empty string to bind to all local
  addresses.
* `port : number` - the port to bind the server to, in the range 1 - 65536.  Port numbers
  1 - 1023 may be reserved for system applications, so probably won't work.
* `callback : function(hostname, port, requestData, sendResponse)` - callback that will
  be executed when requests are received.  See documentation below for description of
  parameters.
* `onStarted : function(hostname, port, serverId)` - callback that is invoked when the
  server is successfully started.  `hostname` and `port` are as supplied in the call
  to `startServer`.  `serverId` is a unique string that identifies the server that has
  been started (future extensions may use this string to identify the server in order
  to change properties or otherwise control it).
* `error : function(text)` - standard Cordova error response callback.  Parameter
  contains a text string that indicates the cause of the error.

The `callback` function passed to `startServer` receives the following parameters:

* `hostname` and `port` are the same values passed to `startServer`.
* `requestData` is an object containing the following fields:
  * `method : string` - the HTTP request method, e.g. "GET" or "POST"
  * `path : string` - the request path, minus the query part.  E.g. if the request
    was for `/my/useful/service?target=globaldomination`, this contains the
    string `"/my/useful/service"`.
  * `parameters : object` - an object containing one property for each request
    parameter; for example given the above request URI, this object would contain
    a property `parameters.target` with string value `"globaldomination"`.  For
    POST requests contains both URL and posted form data parameters.
  * `cookies : object` - similarly to the `parameters` object, but containing cookie
    names and values rather than request parameters.
  * `headers : object` - contains header names and values as object properties.
  * `files : object` - only present if the request was either a POST or a PUT
    request.  For POST requests with multipart file uploads, an object containing
    a property for each uploaded file part name with the path of a temporary file
    containing the uploaded data as its value.  For POST requests where the
    body content was neither a multipart form data nor a URL-encoded form, or for
    any PUT request, the raw data posted is stored in a temporary file and its path
    is stored under the property `content`.
* `sendResponse` is a function that can be used to send a response to this request,
  whose parameters are documented below.  If the response is not available immediately,
  this function reference may be stored temporarily in order to send it when it is, but
  note that if the function is not called within the server's timeout period (which is
  currently hardwired to 60 seconds, although future versions of the plugin will
  likely include a method of changing this) then a default response will be sent.

The parameters to `sendResponse` are:

* `status` - a numeric HTTP status code (e.g. `200` for OK, `404` for not found, `500` for
  internal errors, and so on).  Must conform to a well-known standard status code,
  otherwise the server won't be able to figure out what text to send along with the
  code.
* `contentType` - the MIME type string for the data to send to the client.  For text
  based content types, the `charset` parameter is used to determine what character encoding
  to use to translate the `data` string to bytes for transmission.
* `headers` - a set of additional headers to include in the response, in the same format
  as the headers object received by `callback`.  Note that the server will include
  a minimal set of default headers if they are not specified, so an empty object can be used
  here.
* `data` - a string containing data to send to the client
* `success` - a function called once the response has been processed to send
* `error` - a function called if the response could not be sent for any reason

# Implementation notes

## Android

The Android implementation uses the [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) server.

## iOS

Coming soon.
