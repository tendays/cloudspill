package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.ui.SettingsActivity;
import org.gamboni.cloudspill.domain.Domain;

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

    public boolean checkLink() {
        Log.d(TAG, "Checking server availability at "+ verifiedUrl);
        ConnectivityTestRequest request = new ConnectivityTestRequest(verifiedUrl);
        queue.add(request);
        this.serverInfo = request.getResponse();
        return serverInfo.isOnline();
    }

    public void upload(String folder, String path, Date date, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Uploading "+ body.length +" bytes");
        queue.add(new FileUploadRequest(verifiedUrl +"/item/"+ user +"/" + folder +"/"+ path,
                date,
                body,
                listener,
                onError));
    }

    public ServerInfo getServerInfo() {
        if (this.serverInfo == null) {
            throw new IllegalStateException("call checkLink() before getServerInfo");
        }
        return this.serverInfo;
    }

    public void itemsSince(long id, Response.Listener<Iterable<Domain.Item>> listener, Response.ErrorListener errorListener) {
        queue.add(new ItemsSinceRequest(verifiedUrl, context, domain, id, listener, errorListener));
    }
}
