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
public class ItemsSinceRequest extends AuthenticatingRequest<ItemsSinceRequest.Result> {

    private static final String TAG = "CloudSpill.itemsSince";

    private final Domain domain;

    public interface Result extends Iterable<Domain.Item> {
        Long getLatestUpdate();
    }

    public ItemsSinceRequest(Context context, String url, Domain domain, long millis, Response.Listener<Result> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url +"/item/sinceDate/"+ millis, listener, errorListener);
        this.domain = domain;
    }

    @Override
    protected Response<Result> parseNetworkResponse(final NetworkResponse response) {
        Log.d(TAG, "Received "+ response.data.length +" bytes from server");
        try {
            return Response.<Result>success(new ResponseStream(domain, new BufferedReader(new InputStreamReader(new ByteArrayInputStream(response.data),
                        HttpHeaderParser.parseCharset(response.headers)))),
                HttpHeaderParser.parseCacheHeaders(response));

        } catch (IOException e) {
            return Response.error(new VolleyError(e));
        }
    }

    private static class ResponseStream implements Result, Iterator<Domain.Item> {
        Domain domain;
        BufferedReader in;
        String nextLine = null;
        Long latestUpdate = null;

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
            return (nextLine != null) && nextLine.contains(";");
        }

        @Override
        public Domain.Item next() {
            Domain.Item result = domain.new Item(nextLine);
            readLine();
            if (!hasNext() && nextLine.startsWith("Timestamp:")) {
                latestUpdate = Long.parseLong(nextLine.substring("Timestamp:".length()));
            }
            return result;
        }

        @Override
        public Long getLatestUpdate() {
            return latestUpdate;
        }
    }

    protected void deliverResponse(Result response) {
        Log.d(TAG, "Received response "+ response +" from server");

        super.deliverResponse(response);
    }
}
