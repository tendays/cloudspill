package org.gamboni.cloudspill.server;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.ui.SettingsActivity;
import org.gamboni.cloudspill.domain.Domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.Date;

/**
 * Created by tendays on 25.06.17.
 */

public class CloudSpillServerProxy {

    private static final String TAG = "CloudSpillServer";

    private final String url;
    private final String user;
    private final RequestQueue queue;
    private final Context context;
    private final Domain domain;

    private ServerInfo serverInfo = null;

    public CloudSpillServerProxy(Context context, Domain domain, String url) {
        this.context = context;
        this.domain = domain;
        this.user = SettingsActivity.getUser(context);
        this.url = url;
        this.queue = Volley.newRequestQueue(context);
    }

    /** Object used to synchronise access to verifiedUrl */
    private static final Object urlMonitor = new Object();
    /** Url of the server to which connection has been established. */
    private static String verifiedUrl = null;

    /** TODO call this method in case connection to verifiedUrl fails. */
    public static void invalidateServer() {
        synchronized (urlMonitor) {
            verifiedUrl = null;
        }
    }

    /** Return a new {@link org.gamboni.cloudspill.server.CloudSpillServerProxy} instance. The first time
     * this method is called, all servers will be tried one by one until one responds. Later calls to this
     * method will return a proxy to the same url without checking it is up.
     *
     * @param context the returned proxy will use that context for network access
     * @param listener to inform user of connectivity progress
     * @param domain domain object used by the proxy
     * @return a server, or null if no server could be found.
     */
    public static CloudSpillServerProxy selectServer(Context context, StatusReport listener, Domain domain) {
        // TODO don't spam the servers if they're offline - put a minimal delay before retrying
        // (to reset if user requests refresh)
        synchronized (urlMonitor) {
            if (verifiedUrl != null) {
                return new CloudSpillServerProxy(context, domain, verifiedUrl);
            }
        }
        CloudSpillServerProxy server = null;
        for (final Domain.Server serverEntity : domain.selectServers()) {
            CloudSpillServerProxy testServer = new CloudSpillServerProxy(context, domain, serverEntity.url);
            listener.updateMessage(StatusReport.Severity.INFO, "Connecting to "+ serverEntity.name);
            // First verify we are online
            if (testServer.checkLink()) {
                Log.i(TAG, "Server is up");
                listener.updateMessage(StatusReport.Severity.INFO, "Connected to "+ serverEntity.name);
                server = testServer;
                break;
            } else {
                Log.i(TAG, "No connection to server "+ serverEntity.name +" at "+ serverEntity.url);
            }
        }

        if (server == null) {
            listener.updateMessage(StatusReport.Severity.ERROR, "No connection to server");
        }
        synchronized (urlMonitor) {
            verifiedUrl = (server == null) ? null : server.url;
        }
        return server;
    }

    /** Check availability of the server represented by this proxy, <em>if it hasn't been checked previously</em>.
     * Otherwise return the outcome of the previous {@link #checkLink()} or {@link #recheckLink()} invocation.
     * @return whether communication with the server was successful.
     */
    public boolean checkLink() {
        if (this.serverInfo == null) {
            return recheckLink();
        } else {
            return this.serverInfo.isOnline();
        }
    }

    /** Check availability of the server represented by this proxy, <em>even if it was checked previously</em>. */
    public boolean recheckLink() {
        Log.d(TAG, "Checking server availability at "+ url);
        ConnectivityTestRequest request = new ConnectivityTestRequest(url);
        queue.add(request);
        this.serverInfo = request.getResponse();
        return serverInfo.isOnline();
    }

    private static final int BUF_SIZE = 4096;

    public void upload(String folder, String path, Date date, InputStream body, long bytes, Response.Listener<Long> listener, Response.ErrorListener onError) {
        // TODO [MAJOR] run all this in a separate thread
        Log.d(TAG, "UploadingVideo "+ folder +"/"+ path);
//        String url = "http://192.168.44.189:4567"; // Temporary replacement for testing
//        String url = "http://10.0.0.6:4567"; // Temporary replacement for testing
        OutputStream out = null;
        BufferedReader response = null;
        HttpURLConnection connection = null;
        int loggedPercentage = 0; // latest displayed percentage
        int transmitted = 0; // how many bytes have been pushed so far
        try {
            connection = (HttpURLConnection)new URL(url +"/item/"+ user +"/" + folder +"/"+ path).openConnection();
            connection.setRequestMethod("PUT");
            // Using chunked transfer to prevent caching at server side :(
            connection.setChunkedStreamingMode(1048576);
            //connection.setFixedLengthStreamingMode(bytes);
            connection.setDoOutput(true);
            out = connection.getOutputStream();
            byte[] buffer = new byte[BUF_SIZE];
            int readLen;
            while ((readLen = body.read(buffer)) > 0) {
                out.write(buffer, 0, readLen);
                transmitted += readLen;
                int percentage = (int) (transmitted * 100 / bytes);
                if (percentage/10 > loggedPercentage/10) {
                    Log.d(TAG, "UploadingVideo "+ folder +"/"+ path +" ["+ percentage +"%]");
                    loggedPercentage = percentage;
                }
            }
            response = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String responseText = response.readLine();
            if (responseText == null) {
                Log.e(TAG, "No response");
                onError.onErrorResponse(new VolleyError("No response received from server"));
                return;
            }
            long id = Long.parseLong(responseText);
            Log.i(TAG, "Received id "+ id);
            // To avoid polluting my phone with test ids
            // onError.onErrorResponse(new VolleyError("Video upload temporarily disabled"));
            listener.onResponse(id);
        } catch (IOException e) {
            Log.e(TAG, "Uploading failed", e);
            onError.onErrorResponse(new VolleyError("I/O problem when uploading", e));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Whatever
                }
            }
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    // Whatever
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void upload(String folder, String path, Date date, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Uploading "+ body.length +" bytes");
        queue.add(new FileUploadRequest(url +"/item/"+ user +"/" + folder +"/"+ path,
                date,
                body,
                listener,
                onError));
    }

    public void download(long serverId, FileBuilder target, Response.Listener<byte[]> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Downloading item#"+ serverId +" to "+ target);
        queue.add(new MediaDownloadRequest(url, serverId, listener, onError));
    }

    /** Get information about the server. This method may only be called after {@link #checkLink} or {@link #recheckLink}
     * was invoked on the same object (but it is not necessary those methods have returned true).
     */
    public ServerInfo getServerInfo() {
        if (this.serverInfo == null) {
            throw new IllegalStateException("call checkLink() before getServerInfo");
        }
        return this.serverInfo;
    }

    public void itemsSince(long id, Response.Listener<Iterable<Domain.Item>> listener, Response.ErrorListener errorListener) {
        queue.add(new ItemsSinceRequest(url, context, domain, id, listener, errorListener));
    }
}
