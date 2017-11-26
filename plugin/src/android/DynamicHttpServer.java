package uk.org.dsf.cordova.dynamichttp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.nanohttpd.protocols.http.*;
import org.nanohttpd.protocols.http.content.CookieHandler;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.IStatus;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.util.IHandler;

public class DynamicHttpServer extends CordovaPlugin {
    HashMap<String, ServerContext> servers = new HashMap<String, ServerContext> ();

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startServer")) {
            String hostname = args.getString (0);
            if (hostname != null && hostname.equals ("")) hostname = null;
            int port = args.getInt(1);
            try {
                String serverId = this.startServer (hostname, port, callbackContext);

                JSONObject parameter = new JSONObject();
                parameter.put("serverStarted", true);
                parameter.put("serverId", serverId);
                PluginResult result = new PluginResult(PluginResult.Status.OK, parameter);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
            catch (Exception e) {
                callbackContext.error (e.toString());
            }
            return true;
        }
        else if (action.equals ("sendResponse")) {
            if (args.length() < 3)
            {
                callbackContext.error ("Missing arguments to sendResponse");
                return true;
            }
            ServerContext server = servers.get (args.getString(0));
            if (server == null)
            {
                callbackContext.error ("Unrecognised server id");
                return true;
            }
            try
            {
                server.sendResponse (args.getString(1), args.getJSONObject(2));
                callbackContext.success ();
            }
            catch (Exception e) {
                callbackContext.error (e.toString());
            }
            return true;
        }
        return false;
    }

    private String startServer (String hostname, int port, CallbackContext callbackContext) throws IOException
    {
        ServerContext context = new ServerContext (hostname, port, callbackContext);
        String id = Integer.toHexString (context.hashCode ());
        servers.put (id, context);
        context.start (id);
        return id;
    }

    private class ServerContext implements IHandler<IHTTPSession, Response>
    {
        private NanoHTTPD server;
        private CallbackContext callbackContext;
        private String serverId;
        private long timeout = 60000L;   // FIXME provide an API to change this

        // We use an ArrayBlockingQueue of length 1 to transfer each response from the Cordova thread to the
        // server request handling thread.
        // CompletableFuture would be more appropriate, but isn't available on Android versions < Nougat, whereas
        // ability to run on at the very latest KitKat is considered desirable here.
        private Map<String, ArrayBlockingQueue<JSONObject>> requestResponseQueues;

        public ServerContext (String hostname, int port, CallbackContext callbackContext)
        {
            server = hostname != null ? new NanoHTTPD (hostname, port) : new NanoHTTPD(port);
            server.setHTTPHandler (this);
            this.callbackContext = callbackContext;
            requestResponseQueues = new ConcurrentHashMap <String, ArrayBlockingQueue<JSONObject>> ();
        }
        public void start (String serverId) throws IOException
        {
            this.serverId = serverId;
            server.start (30000, true);
        }
        public synchronized Response handle (IHTTPSession request)
        {
            ArrayBlockingQueue<JSONObject> responseQueue = new ArrayBlockingQueue<JSONObject>(1, false);
            String requestId = Integer.toHexString (System.identityHashCode(responseQueue));
            requestResponseQueues.put (requestId, responseQueue);
            try
            {
                // generate an object to encapsulate the request data for javascript access:
                JSONObject requestDTO = new JSONObject ();
                requestDTO.put ("serverId", serverId);
                requestDTO.put ("requestId", requestId);
                requestDTO.put ("requestMethod", request.getMethod().name ());
                requestDTO.put ("requestPath", request.getUri());
                requestDTO.put ("requestCookies", buildCookieMap (request.getCookies()));
                requestDTO.put ("requestHeaders", new JSONObject (request.getHeaders()));
                if (request.getMethod() == Method.POST)
                {
                    // POST methods need special handling.  POST request bodies may contain:
                    // * an x-www-form-urlencoded object, containing additional parameters
                    // * a multipart form data object, which may contain uploaded files in addition to regular parameters
                    // * or raw data in another format
                    // the HTTPSession has a method "parseBody(Map)" to handle these possibilities. unfortunately, it's
                    // inadequately documented, but reading the source reveals it performs the following actions:
                    // * form parameters get added to the same map that already contains the URL parameters
                    // * multipart form parameters also get added to the same map
                    // * multipart form file uploads get stored in a temporary file and the filename is placed in the map
                    //   passed as the parameter to the method under a key which is the part name (optionally suffixed with
                    //   a number starting from '2' for multiple files uploaded under the same name)
                    // * raw data is stored in a temporary file and the filename is placed in the map under the key "postData"
                    //
                    // we pass this along to the handler script in a similar format, except that for easier development
                    // of handlers that can work with either POST or PUT we duplicate "postData" to "content" wherever it is found
                    // and is the only entry in the map.
                    Map<String,String> files = new HashMap<String,String>();
                    request.parseBody (files);
                    if (files.size() == 1 && files.containsKey("postData"))
                        files.put("content", files.get("postData"));
                    requestDTO.put ("requestFiles", files);
                }
                else if (request.getMethod() == Method.PUT)
                {
                    // as above, we use parseBody.  for PUT requests, parseBody stores the uploaded body in a temporary
                    // file under the key "content"; we simply pass this along unchanged.
                    Map<String,String> files = new HashMap<String,String>();
                    request.parseBody (files);
                    requestDTO.put ("requestFiles", files);
                }
                requestDTO.put ("requestParameters", request.getParameters ());

                // send the object back to the webview
                PluginResult result = new PluginResult(PluginResult.Status.OK, requestDTO);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);

                // wait for a response
                JSONObject response = responseQueue.poll (timeout, TimeUnit.MILLISECONDS);
                if (response == null)
                {
                    // send timeout error
                    return generateResponse (
                        Status.INTERNAL_ERROR,
                        "text/plain",
                        "Timeout waiting for response from application");
                }
                else
                {
                    // send response generated by app
                    Response r = generateResponse (
                        Status.lookup(response.getInt("status")),
                        response.getString("contentType"),
                        response.getString("data")
                    );
                    JSONObject headers = response.getJSONObject("headers");
                    if (headers != null)
                    {
                        // Android's JSONObject is out-of-date, and doesn't support keySet(), so emulate it:
                        for (String headerName : JSONUtils.arrayToStringList(headers.names()))
                            r.addHeader(headerName, headers.getString(headerName));
                    }
                    return r;
                }
            }
            catch (Exception e)
            {
                return generateResponse (Status.INTERNAL_ERROR, "text/plain", e.toString ());
            }
            finally
            {
                requestResponseQueues.remove (requestId);
            }
        }

        private Response generateResponse (IStatus status, String contentType, String content)
        {
            if (status == null) status = Status.INTERNAL_ERROR;
            return Response.newFixedLengthResponse (status, contentType, content);
        }

        private JSONObject buildCookieMap (CookieHandler cookies) throws JSONException
        {
            JSONObject result = new JSONObject ();
            for (String name : cookies)
                result.put (name, cookies.read(name));
            return result;
        }

        public void sendResponse (String requestId, JSONObject response) throws InterruptedException
        {
            ArrayBlockingQueue queue = requestResponseQueues.get(requestId);
            if (queue != null) queue.offer (response, timeout, TimeUnit.MILLISECONDS);
        }
    }
}
