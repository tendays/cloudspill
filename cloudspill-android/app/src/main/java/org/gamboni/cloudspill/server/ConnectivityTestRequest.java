package org.gamboni.cloudspill.server;

import android.content.Context;
import android.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.gamboni.cloudspill.SettingsActivity;

/**
 * @author tendays
 */

public class ConnectivityTestRequest extends StringRequest {
    /** Interface implemented by the client to receive the result. */
    public interface Listener {
        /** Set to true if connectivity was etablished, false otherwise. */
        void setResult(boolean online);
    }

    public ConnectivityTestRequest(Context context, final Listener listener) {
        super(SettingsActivity.getServerUrl(context) + "/ping", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                listener.setResult(true);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.setResult(false);
            }
        });
    }

    public void parseNetworkResponse() {}

    public void deliverResponse(String r) {}
}
