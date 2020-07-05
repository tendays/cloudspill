package org.gamboni.cloudspill.server;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class MediaDownloadRequest extends AuthenticatingRequest<byte[]> {

    public MediaDownloadRequest(Context context, CloudSpillApi api, long serverId,
                                Response.Listener<byte[]> listener, Response.ErrorListener errorListener,
                                CloudSpillApi.Size thumbnailSize) {
        super(context, Method.GET, buildUrl(api, serverId, thumbnailSize), listener, errorListener);
        setRetryPolicy(new DefaultRetryPolicy(/*timeout*/30_000, /*retries*/3, /*backoff multiplier*/2));
    }

    private static String buildUrl(CloudSpillApi api, long serverId, CloudSpillApi.Size thumbnailSize) {
        if (thumbnailSize == null) { // full image
            return api.getImageUrl(serverId, new ItemCredentials.UserPassword());
        } else { // thumbnail
            return api.getThumbnailUrl(serverId, new ItemCredentials.UserPassword(), thumbnailSize);
        }
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        // WARN - caching would be redundant here, even if the server allows it
        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }
}
