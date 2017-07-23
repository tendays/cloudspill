package org.gamboni.cloudspill.server;

import android.content.Context;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.gamboni.cloudspill.ui.SettingsActivity;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringRequest {

    private final Listener listener;

    private static class Listener implements Response.Listener<String>, Response.ErrorListener {
        private Boolean response = null;
        @Override
        public synchronized void onErrorResponse(VolleyError error) {
            response = false;
            this.notify();
        }

        @Override
        public synchronized void onResponse(String serverResponse) {
            response = true;
            this.notify();
        }

        private synchronized boolean getResponse() {
            while (response == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    response = false;
                }
            }
            return response;
        }
    }

    public ConnectivityTestRequest(final String url) {
        this(url + "/ping", new Listener());
    }

    private ConnectivityTestRequest(String pingUrl, Listener listener) {
        super(pingUrl, listener, listener);
        this.listener = listener;
    }

    public boolean getResponse() {
        return listener.getResponse();
    }
}
