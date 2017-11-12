package org.gamboni.cloudspill.server;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

/**
 * @author tendays
 */
public class MediaDownloadRequest extends Request<byte[]> {

    private final Response.Listener<byte[]> listener;

    public MediaDownloadRequest(String serverUrl, long serverId,
                                Response.Listener<byte[]> listener, Response.ErrorListener errorListener,
                                Integer thumbnailSize) {
        super(buildUrl(serverUrl, serverId, thumbnailSize), errorListener);
        this.listener = listener;
    }

    private static String buildUrl(String serverUrl, long serverId, Integer thumbnailSize) {
        if (thumbnailSize == null) { // full version
            return serverUrl +"/item/"+ serverId;
        } else { // thumbnail
            return serverUrl +"/thumbs/"+ thumbnailSize +"/"+ serverId;
        }
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        // WARN - caching would be redundant here, even if the server allows it
        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(byte[] response) {
        listener.onResponse(response);
    }
}
