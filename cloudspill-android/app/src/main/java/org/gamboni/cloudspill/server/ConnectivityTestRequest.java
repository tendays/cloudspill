package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;

import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.domain.Splitter;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringBasedAuthenticatingRequest<String> {

    private final Listener listener;
    private static final String PREAMBLE = "CloudSpill server.";

    private static class Listener implements Response.Listener<String>, Response.ErrorListener {
        private ServerInfo response = null;
        @Override
        public synchronized void onErrorResponse(VolleyError error) {
            response = ServerInfo.offline();
            this.notify();
        }

        @Override
        public synchronized void onResponse(String serverResponse) {
            final Splitter splitter = new Splitter(serverResponse, '\n');
            String preamble = splitter.getString();
            if (!preamble.equals(PREAMBLE)) {
                Log.w(TAG, "Not connecting to server with unexpected preamble "+ preamble);
                response = ServerInfo.offline(); // TODO LOG
            }
            Integer version = null;
            String url = null;
            for (String line : splitter.allRemainingTo(new ArrayList<String>())) {
                int colon = line.indexOf(":");
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (key.equals("Data-Version")) {
                    version = Integer.parseInt(value);
                } else if (key.equals("Url")) {
                    url = value;
                } // else: assume key provided by later version of the server - ignore
            }
            if (version == null || url == null) {
                Log.w(TAG, "Not connecting to server not specifying version or url: " + version +", "+ url);
                response = ServerInfo.offline();
            } else {
                response = ServerInfo.online(version, url);
            }
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
        this(context, url + "/ping", new Listener());
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
