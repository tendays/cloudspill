package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;

import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringBasedAuthenticatingRequest<String> {

    private final Listener listener;
    private static final String PREAMBLE = "CloudSpill server.\nData-Version: ";

    private static class Listener implements Response.Listener<String>, Response.ErrorListener {
        private ServerInfo response = null;
        @Override
        public synchronized void onErrorResponse(VolleyError error) {
            response = ServerInfo.offline();
            this.notify();
        }

        @Override
        public synchronized void onResponse(String serverResponse) {
            if (!serverResponse.startsWith(PREAMBLE)) {
                response = ServerInfo.offline(); // TODO LOG
            }
            response = ServerInfo.online(Integer.parseInt(serverResponse.substring(PREAMBLE.length())));
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
