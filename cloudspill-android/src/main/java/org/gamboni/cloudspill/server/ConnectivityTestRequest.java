package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.shared.api.PingResponseHandler;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.shared.util.Splitter;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.util.ArrayList;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringBasedAuthenticatingRequest<String> {

    private final Listener listener;

    private static class Listener implements Response.Listener<String>, Response.ErrorListener {
        private ServerInfo response = null;
        @Override
        public synchronized void onErrorResponse(VolleyError error) {
            response = ServerInfo.offline();
            this.notify();
        }

        @Override
        public synchronized void onResponse(String serverResponse) {
            this.response = new PingResponseHandler() {
                protected void warn(String message) {
                    Log.w(TAG, message);
                }
            }.parse(serverResponse);

            this.notify();
        }

        private synchronized ServerInfo getResponse() {
            while (response == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    response = ServerInfo.offline();
                }
            }
            return response;
        }
    }

    public ConnectivityTestRequest(Context context, final String url) {
        this(context, url + CloudSpillApi.PING, new Listener());
    }

    private ConnectivityTestRequest(Context context, String pingUrl, Listener listener) {
        super(context, Method.GET, pingUrl, listener, listener);
        this.listener = listener;
    }

    public ServerInfo getResponse() {
        return listener.getResponse();
    }

    @Override
    protected String parseResponse(String response) {
        return response;
    }
}
