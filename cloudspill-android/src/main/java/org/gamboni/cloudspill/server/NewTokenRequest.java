package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.PingResponseHandler;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.IOException;

/**
 * @author tendays
 */
public class NewTokenRequest extends Request<String> {

    private final Listener listener;

    private static class Listener implements Response.Listener<String>, Response.ErrorListener {
        String error = null;
        String response = null;
        @Override
        public synchronized void onErrorResponse(VolleyError error) {
            this.error = error.toString();
            this.notify();
        }

        @Override
        public synchronized void onResponse(String response) {
            this.response = response;
            this.notify();
        }

        private synchronized void waitForResponse() {
            while (response == null && error == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    error = e.toString();
                }
            }
        }
    }

    public NewTokenRequest(Context context, CloudSpillApi api) {
        this(context, api, new Listener());
    }

    private NewTokenRequest(Context context, CloudSpillApi api, Listener listener) {
        super(Method.POST, api.newToken(SettingsActivity.getUser(context)), listener);
        this.listener = listener;
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        try {
            return Response.success(new String(response.data, HttpHeaderParser.parseCharset(response.headers)),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (IOException e) {
            return Response.error(new VolleyError(e));
        }
    }

    @Override
    protected void deliverResponse(String response) {
        listener.onResponse(response);
    }

    public String getError() {
        listener.waitForResponse();
        return listener.error;
    }

    public String getResponse() {
        listener.waitForResponse();
        return listener.response;
    }
}
