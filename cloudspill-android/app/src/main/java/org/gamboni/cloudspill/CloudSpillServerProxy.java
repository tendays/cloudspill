package org.gamboni.cloudspill;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.File;

/**
 * Created by tendays on 25.06.17.
 */

public class CloudSpillServerProxy {

    private static final String TAG = "CloudSpillServer";

    private final String url;
    private final RequestQueue queue;

    public CloudSpillServerProxy(Context context, String url) {
        this.url = url;
        this.queue = Volley.newRequestQueue(context);
    }

    public void upload(String folder, String path, byte[] body, Response.Listener<Long> listener, Response.ErrorListener onError) {
        queue.add(new FileUploadRequest(url + "/item/"+ folder +"/"+ path,
                body,
                listener,
                onError));
    }
}
