package org.gamboni.cloudspill.server;

import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.gamboni.cloudspill.domain.ServerInfo;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringRequest {

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

    public ConnectivityTestRequest(final String url) {
        this(url + "/ping", new Listener());
    }

    private ConnectivityTestRequest(String pingUrl, Listener listener) {
        super(pingUrl, listener, listener);
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> result = new HashMap<>();
        result.put("Authorization", "Basic "+ Base64.encodeToString("test:secret".getBytes(), Base64.NO_WRAP));
        return result;
    }

    public ServerInfo getResponse() {
        return listener.getResponse();
    }
}
