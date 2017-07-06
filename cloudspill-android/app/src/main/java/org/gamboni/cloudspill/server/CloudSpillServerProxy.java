package org.gamboni.cloudspill.server;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.SettingsActivity;

/**
 * Created by tendays on 25.06.17.
 */

public class CloudSpillServerProxy {

    private static final String TAG = "CloudSpillServer";

    private final String url;
    private final String user;
    private final RequestQueue queue;
    private final Context context;

    public CloudSpillServerProxy(Context context) {
        this.context = context;
        this.user = SettingsActivity.getUser(context);
        this.url = SettingsActivity.getServerUrl(context);
        this.queue = Volley.newRequestQueue(context);
    }

    public void checkLink(final ConnectivityTestRequest.Listener feedback) {
        Log.d(TAG, "Checking server availability");
        queue.add(new ConnectivityTestRequest(context, feedback));
    }

    public void upload(String folder, String path, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        queue.add(new FileUploadRequest(url +"/"+ user + "/item/"+ folder +"/"+ path,
                body,
                listener,
                onError));
    }
}
