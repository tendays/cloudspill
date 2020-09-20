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
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.ClientUser;
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

        final String token = SettingsActivity.getAuthenticationToken(context);
        if (!token.isEmpty()) {
            Log.d(TAG, "Sending request using token '"+ token.substring(0, 5) +"...'");
            new ItemCredentials.UserToken(new ClientUser(SettingsActivity.getUser(context)), token)
                    .setHeaders(result, AndroidBase64Encoder.INSTANCE);
        }

        return result;
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }
}
