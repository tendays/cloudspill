package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tendays
 */

public abstract class AuthenticatingRequest<T> extends Request<T> {
    protected final Context context;
    private final Response.Listener<T> listener;
    protected static final String TAG = "CloudSpill.Network";

    protected AuthenticatingRequest(Context context, int method, String url, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.context = context;
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> result = new HashMap<>();

        final String credentials = SettingsActivity.getUser(context) + ":" + SettingsActivity.getPassword(context);
        result.put("Authorization", "Basic "+ Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP));

        return result;
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }
}
