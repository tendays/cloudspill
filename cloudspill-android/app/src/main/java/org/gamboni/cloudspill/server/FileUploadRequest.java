package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tendays on 25.06.17.
 */

public class FileUploadRequest extends AuthenticatingRequest<Long> {
    private final Date date;
    private final byte[] body;

    private static final String TAG = "CloudSpill.Upload";

    /**
     * Make a PUT request and return the id of the created item.
     *
     * @param url URL of the request to make
     */
    public FileUploadRequest(Context context, String url, Date date, byte[] body, Response.Listener<Long> listener, Response.ErrorListener errorListener) {
        super(context, Method.PUT, url, listener, loggingWrapper(url, errorListener));
        this.body = body;
        this.date = date;
        if (url.contains("invalid")) { throw new IllegalArgumentException(); }
        Log.d(TAG, "Created request to "+ url);
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = super.getHeaders();
        headers.put("X-CloudSpill-Timestamp", Long.toString(date.getTime()));
        return headers;
    }

    private static Response.ErrorListener loggingWrapper(final String url, final Response.ErrorListener delegate) {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // networkResponse is unset if the server could not be reached
                Log.e(TAG, "Received "+
                                (error.networkResponse == null ? "no response" :
                        "status "+ error.networkResponse.statusCode) +
                        " for request at "+ url +": "+ error.getMessage());
                delegate.onErrorResponse(error);
            }
        };
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    protected Response<Long> parseNetworkResponse(NetworkResponse response) {
        try {
            Log.d(TAG, "Received response from server");
            return Response.success(Long.parseLong(new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers))),
                    HttpHeaderParser.parseCacheHeaders(response)
                    );

        } catch (UnsupportedEncodingException e) {
            return Response.error(new VolleyError(e));
        }
    }

    protected void deliverResponse(Long response) {
        Log.d(TAG, "Received response "+ response +" from server");

        super.deliverResponse(response);
    }
}