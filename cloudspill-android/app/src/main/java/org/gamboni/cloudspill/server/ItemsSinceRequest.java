package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

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
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

/** Get the list of items that have been created after (I have a greater id than) the given one.
 *
 * @author tendays
 */
public class ItemsSinceRequest extends Request<Iterable<Domain.Item>> {

    private static final String TAG = "CloudSpill.itemsSince";

    private final Response.Listener<Iterable<Domain.Item>> listener;
    private final Domain domain;

    public ItemsSinceRequest(String url, Context context, Domain domain, long id, Response.Listener<Iterable<Domain.Item>> listener, Response.ErrorListener errorListener) {
        super(Method.GET, url +  "/item/since/"+ id, errorListener);
        this.listener = listener;
        this.domain = domain;
    }

    @Override
    protected Response<Iterable<Domain.Item>> parseNetworkResponse(final NetworkResponse response) {
        Log.d(TAG, "Received "+ response.data.length +" bytes from server");
        try {
            return Response.<Iterable<Domain.Item>>success(new ResponseStream(domain, new BufferedReader(new InputStreamReader(new ByteArrayInputStream(response.data),
                        HttpHeaderParser.parseCharset(response.headers)))),
                HttpHeaderParser.parseCacheHeaders(response));

        } catch (IOException e) {
            return Response.error(new VolleyError(e));
        }
    }

    private static class ResponseStream implements Iterable<Domain.Item>, Iterator<Domain.Item> {
        Domain domain;
        BufferedReader in;
        String nextLine = null;

        ResponseStream(Domain domain, BufferedReader in) {
            this.domain = domain;
            this.in = in;

            readLine();
        }

        private void readLine() {
            try {
                nextLine = in.readLine();
            } catch (IOException e) {
                nextLine = null;
            }
        }

        public Iterator<Domain.Item> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return (nextLine != null);
        }

        @Override
        public Domain.Item next() {
            Domain.Item result = domain.new Item(nextLine);
            readLine();
            return result;
        }
    }

    protected void deliverResponse(Iterable<Domain.Item> response) {
        Log.d(TAG, "Received response "+ response +" from server");

        listener.onResponse(response);
    }
}
