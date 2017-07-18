package org.gamboni.cloudspill.server;

import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.gamboni.cloudspill.domain.Domain;

import java.io.UnsupportedEncodingException;

/**
 * @author tendays
 */

public class ItemsSinceRequest extends Request<Iterable<Domain.Item>> {

    private static final String TAG = "CloudSpill.itemsSince";

    public ItemsSinceRequest(int id, Response.Listener<Iterable<Domain.Item>> listener, Response.ErrorListener errorListener) {
        super(Method.GET, "url", errorListener);
//        Log.d(TAG, "Created request to "+ url);
    }


    @Override
    protected Response<Iterable<Domain.Item>> parseNetworkResponse(NetworkResponse response) {
        try {
            Log.d(TAG, "Received response from server");
            new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            //HttpHeaderParser.parseCacheHeaders(response);
            return null;/*Response.success(Long.parseLong(
            );*/

        } catch (UnsupportedEncodingException e) {
            return Response.error(new VolleyError(e));
        }
    }

    protected void deliverResponse(Iterable<Domain.Item> response) {
        Log.d(TAG, "Received response "+ response +" from server");

//        listener.onResponse(response);
    }
}
