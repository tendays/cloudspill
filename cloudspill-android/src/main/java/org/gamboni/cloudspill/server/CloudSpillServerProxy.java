package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.ui.SettingsActivity;
import org.gamboni.cloudspill.domain.Domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;

/**
 * Created by tendays on 25.06.17.
 */

public class CloudSpillServerProxy {

    private static final String TAG = "CloudSpillServer";

    private final CloudSpillApi api;
    private final String user;
    private final RequestQueue queue;
    private final Context context;
    private final Domain domain;

    private ServerInfo serverInfo = null;

    public CloudSpillServerProxy(Context context, Domain domain, String url) {
        this.context = context;
        this.domain = domain;
        this.user = SettingsActivity.getUser(context);
        this.api = new CloudSpillApi(url);
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
            CloudSpillServerProxy testServer = new CloudSpillServerProxy(context, domain, serverEntity.getUrl());
            listener.updateMessage(StatusReport.Severity.INFO, "Connecting to "+ serverEntity.getName());
            // First verify we are online
            if (testServer.checkLink()) {
                Log.i(TAG, "Server is up");
                listener.updateMessage(StatusReport.Severity.INFO, "Connected to "+ serverEntity.getName());
                server = testServer;
                break;
            } else {
                Log.i(TAG, "No connection to server "+ serverEntity.getName() +" at "+ serverEntity.getUrl());
            }
        }

        if (server == null) {
            listener.updateMessage(StatusReport.Severity.ERROR, "No connection to server");
        }
        synchronized (urlMonitor) {
            verifiedUrl = (server == null) ? null : server.api.getBaseUrl();
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
        Log.d(TAG, "Checking server availability at "+ api.getBaseUrl());
        ConnectivityTestRequest request = new ConnectivityTestRequest(context, api);
        queue.add(request);
        this.serverInfo = request.getResponse();
        if (serverInfo.isOnline()) {
            // Cache latest public url of current server
            SettingsActivity.setLastServerVersion(context, serverInfo);
        }
        return serverInfo.isOnline();
    }

    private static final int BUF_SIZE = 4096;

    public void upload(final String folder, final String path, Date date, ItemType type, final InputStream body,
                       final long bytes, final Response.Listener<Long> listener, final Response.ErrorListener onError) {
        // TODO [MAJOR][Performance] run all this in a separate thread
        Log.d(TAG, "UploadingVideo "+ folder +"/"+ path);

        try {
            new AuthenticatingConnection(context,
                AuthenticatingConnection.RequestMethod.PUT,
                api.upload(user, folder, path))
            .setHeader(AuthenticatingConnection.RequestHeader.TIMESTAMP, Long.toString(date.getTime()))
            .setHeader(AuthenticatingConnection.RequestHeader.TYPE, type.name())
            // Using chunked transfer to prevent caching at server side :(
            .setChunkedStreamingMode(1048576)
            .setDoOutput()
            .connect(new AuthenticatingConnection.Session() {
                @Override
                public void run(AuthenticatingConnection.Connected connected) throws IOException {
                    /*
                    DANGER: this creates zero-byte files in server!
                    final InputStream input = connected.getInput();
                    Log.d(TAG, "Eager input has "+ input.available() +" bytes ready");
                    */
                    //final BufferedReader eagerRead = new BufferedReader(new InputStreamReader(input));

                    BufferedReader response = null;

                    int loggedPercentage = 0; // latest displayed percentage
                    int transmitted = 0; // how many bytes have been pushed so far

                    OutputStream out = connected.getOutput();
                    byte[] buffer = new byte[BUF_SIZE];
                    int readLen;
                    try {
                        while ((readLen = body.read(buffer)) > 0) {
                            out.write(buffer, 0, readLen);
                            transmitted += readLen;
                            int percentage = (int) (transmitted * 100.0 / bytes);
                            if (percentage / 10 > loggedPercentage / 10) {
                                Log.d(TAG, "UploadingVideo " + folder + "/" + path + " [" + percentage + "%]");
                                loggedPercentage = percentage;
                            }
                        }
                    } catch (IOException writeException) {
                        Log.w(TAG, "Exception writing message body. This may be expected when uploading an already existing item", writeException);
                    }
                    response = new BufferedReader(new InputStreamReader(connected.getInput()));
                    String responseText = response.readLine();
                    if (responseText == null) {
                        Log.e(TAG, "No response");
                        onError.onErrorResponse(new VolleyError("No response received from server"));
                        return;
                    }
                    long id = Long.parseLong(responseText);
                    Log.i(TAG, "Received id " + id);
                    listener.onResponse(id);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Uploading failed", e);
            onError.onErrorResponse(new VolleyError("I/O problem when uploading", e));
        }
    }

    public void upload(String folder, String path, Date date, ItemType type, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Uploading "+ body.length +" bytes");
        queue.add(new FileUploadRequest(context, api.upload(user, folder, path),
                date,
                type,
                body,
                listener,
                onError));
    }

    public interface StreamResponseListener {
        void onResponse(int length, InputStream stream);
    }

    public void stream(long serverId, final StreamResponseListener listener, Response.ErrorListener onError) {
        Log.d(TAG, "Streaming item#"+ serverId);
        try {
            new AuthenticatingConnection(context, AuthenticatingConnection.RequestMethod.GET,
                    api.getImageUrl(serverId, new ItemCredentials.UserPassword()))
                    .connect(new AuthenticatingConnection.Session() {
                        @Override
                        public void run(AuthenticatingConnection.Connected connected) throws IOException {
                            listener.onResponse(connected.size(), connected.getInput());
                        }
                    });
        } catch (IOException e) {
            Log.e(TAG, "Streaming file failed", e);
        }
    }

    public void downloadThumb(long serverId, CloudSpillApi.Size thumbSize, Response.Listener<byte[]> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Downloading thumb#"+ serverId);
        queue.add(new MediaDownloadRequest(context, api, serverId, listener, onError, thumbSize));
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

    public void itemsSince(long millis, Response.Listener<ItemsSinceRequest.Result> listener, Response.ErrorListener errorListener) {
        queue.add(new ItemsSinceRequest(context, api, domain, millis, listener, errorListener));
    }

    public void tag(Domain.Tag tag, boolean create, Response.Listener<Void> listener, Response.ErrorListener errorListener) {
        queue.add(new TagRequest(context, api, tag.getItem().getServerId(), tag.get(Domain.TagSchema.TAG),
                create,
                listener, errorListener));
    }
}
