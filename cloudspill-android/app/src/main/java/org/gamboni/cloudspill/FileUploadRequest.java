package org.gamboni.cloudspill;

import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;

/**
 * Created by tendays on 25.06.17.
 */

public class FileUploadRequest extends Request<Long> {
    private final Response.Listener<Long> listener;
    private final byte[] body;

    private static final String TAG = "FileUploadRequest";

    /**
     * Make a PUT request and return the id of the created item.
     *
     * @param url URL of the request to make
     */
    public FileUploadRequest(String url, byte[] body, Response.Listener<Long> listener, Response.ErrorListener errorListener) {
        super(Method.PUT, url, loggingWrapper(url, errorListener));
        this.body = body;
        this.listener = listener;
    }

    private static Response.ErrorListener loggingWrapper(final String url, final Response.ErrorListener delegate) {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Received status "+ error.networkResponse.statusCode +" for request at "+ url);
            }
        };
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    protected Response<Long> parseNetworkResponse(
            NetworkResponse response) {
        try {
            return Response.success(Long.parseLong(new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers))),
                    HttpHeaderParser.parseCacheHeaders(response)
                    );

        } catch (UnsupportedEncodingException e) {
            return Response.error(new VolleyError(e));
        }
    }

    protected void deliverResponse(Long response) {
        listener.onResponse(response);
    }
}