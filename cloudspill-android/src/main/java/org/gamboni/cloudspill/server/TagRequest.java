package org.gamboni.cloudspill.server;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;

import org.gamboni.cloudspill.domain.Domain;

import java.nio.charset.StandardCharsets;

/**
 * @author tendays
 */

public class TagRequest extends StringBasedAuthenticatingRequest<Void> {
    private final String tag;
    private final boolean create;

    public TagRequest(Context context, String serverUrl, long itemId, String tag, boolean create,
                      Response.Listener<Void> listener, Response.ErrorListener errorListener) {
        super(context, Method.PUT, serverUrl +  "/item/"+ itemId +"/tags", listener, errorListener);
        this.tag = tag;
        this.create = create;
    }

    @Override
    public byte[] getBody() {
        return (create ? tag : "-" + tag).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected Void parseResponse(String response) {
        /* The server response carries no information other than the status code. */
        return null;
    }
}
