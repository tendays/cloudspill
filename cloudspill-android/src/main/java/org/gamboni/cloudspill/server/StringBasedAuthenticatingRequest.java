package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.IOException;

/**
 * @author tendays
 */

public abstract class StringBasedAuthenticatingRequest<T> extends AuthenticatingRequest<T> {

    protected StringBasedAuthenticatingRequest(Context context, int method, String url, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(context, method, url, listener, errorListener);
    }

    protected abstract T parseResponse(String response);

    @Override
    protected Response<T> parseNetworkResponse(final NetworkResponse response) {
        Log.d(TAG, "Received "+ response.data.length +" bytes from server");
        try {
            return Response.<T>success(
                    parseResponse(
                            new String(response.data, HttpHeaderParser.parseCharset(response.headers))),
                    HttpHeaderParser.parseCacheHeaders(response));

        } catch (IOException e) {
            return Response.error(new VolleyError(e));
        }
    }
}
