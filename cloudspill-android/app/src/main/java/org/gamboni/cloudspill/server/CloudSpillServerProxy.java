package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;

import org.gamboni.cloudspill.ui.SettingsActivity;
import org.gamboni.cloudspill.domain.Domain;

import java.util.ArrayList;

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

    public CloudSpillServerProxy(Context context, Domain domain, String url) {
        this.context = context;
        this.domain = domain;
        this.user = SettingsActivity.getUser(context);
        this.url = url;
        this.queue = Volley.newRequestQueue(context);
    }

    public boolean checkLink() {
        Log.d(TAG, "Checking server availability at "+ url);
        ConnectivityTestRequest request = new ConnectivityTestRequest(url);
        queue.add(request);
        return request.getResponse();
    }

    public void upload(String folder, String path, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        Log.d(TAG, "Uploading "+ body.length +" bytes");
        queue.add(new FileUploadRequest(url +"/item/"+ user +"/" + folder +"/"+ path,
                body,
                listener,
                onError));
    }

    public void itemsSince(long id, Response.Listener<Iterable<Domain.Item>> listener, Response.ErrorListener errorListener) {
        queue.add(new ItemsSinceRequest(url, context, domain, id, listener, errorListener));
    }
}
