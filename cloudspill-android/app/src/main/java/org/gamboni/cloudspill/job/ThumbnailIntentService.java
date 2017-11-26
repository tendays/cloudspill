package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** This service is responsible for acquiring thumbnails for local or remote items and passing them to
 * callbacks in the form of {@link Bitmap} objects.
 *
 * @author tendays
 */

public class ThumbnailIntentService extends IntentService {

    private static final String TAG = "CloudSpill.Thumbnails";

    public static final String POSITION_PARAM = "position";
    private static final int THUMB_SIZE = 90;

    private Domain domain;
    private List<Domain.Item> itemList;
    private AbstractDomain.Query<Domain.Item> itemQuery;

    private static final LruCache<Integer, BitmapWithItem> memoryCache;

    static {
        // Source: https://developer.android.com/topic/performance/graphics/cache-bitmap.html

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        memoryCache = new LruCache<Integer, BitmapWithItem>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, BitmapWithItem entry) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return entry.bitmap.getByteCount() / 1024;
            }
        };
    }

    private static class BitmapWithItem {
        final Domain.Item item;
        final Bitmap bitmap;

        public BitmapWithItem(Domain.Item item, Bitmap bitmap) {
            this.item = item;
            this.bitmap = bitmap;
        }
    }

    public ThumbnailIntentService() {
        super(ThumbnailIntentService.class.getSimpleName());
    }

    public interface Callback {
        void setItem(Domain.Item item);
        void setThumbnail(Bitmap bitmap);
    }

    // Using a Set in case callbacks define equality, to avoid redundant invocations
    private static final Map<String, Set<Callback>> callbacks = new HashMap<>();
    private static final Map<Callback, String> callbackKeys = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        this.domain = new Domain(this);
        itemQuery = domain.selectItems();
        this.itemList = itemQuery.orderDesc(Domain.Item._DATE).list();
    }

    @Override
    public void onDestroy() {
        itemList = null;

        itemQuery.close();
        itemQuery = null;

        domain.close();
        domain = null;

        super.onDestroy();
    }

    private static String key(int position) {
        return String.valueOf(position);
    }

    private static void registerCallback(int position, Callback callback) {
        String key = key(position);
        synchronized (callbacks) {
            Set<Callback> set = callbacks.get(key);
            if (set == null) {
                set = new HashSet<>();
                callbacks.put(key, set);
            }
            set.add(callback);
            callbackKeys.put(callback, key);
        }
    }

    public static void cancelCallback(Callback callback) {
        synchronized (callbacks) {
            String key = callbackKeys.remove(callback);
            Set<Callback> set = callbacks.get(key);
            if (set != null) {
                set.remove(callback);
            }
        }
    }

    private static boolean hasCallbacks(int position) {
        String key = key(position);
        synchronized (callbacks) {
            Set<Callback> result = callbacks.get(key);
            return (result != null) && result.size() > 0;
        }
    }

    private static Set<Callback> peekCallbacks(int position) {
        String key = key(position);
        synchronized (callbacks) {
            Set<Callback> result = callbacks.get(key);
            Set<Callback> copy = new HashSet<>();
            if (result != null) {
                copy.addAll(result);
            }
            return copy;
        }
    }

    private static Set<Callback> getCallbacks(int position) {
        String key = key(position);
        synchronized (callbacks) {
            Set<Callback> result = callbacks.remove(key);
            return (result == null) ? Collections.<Callback>emptySet() : result;
        }
    }

    private static void publishItem(Set<Callback> callbacks, Domain.Item item) {
        for (Callback callback : callbacks) {
            callback.setItem(item);
        }
    }

    private static void publishBitmap(Set<Callback> callbacks, Bitmap bitmap) {
        for (Callback callback : callbacks) {
            callback.setThumbnail(bitmap);
        }
    }

    public static void loadThumbnail(Context context, int position, Callback callback) {
        BitmapWithItem cached = memoryCache.get(position);

        if (cached != null) {
            final Set<Callback> callbackSet = Collections.singleton(callback);
            publishItem(callbackSet, cached.item);
            publishBitmap(callbackSet, cached.bitmap);
            return;
        }

        Log.d(TAG, "Loading "+ position);//file);
        Intent intent = new Intent(context, ThumbnailIntentService.class);
        intent.putExtra(POSITION_PARAM, position);

        registerCallback(position, callback);

        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) { return; } // Intent should not be null, but well there's nothing to do if it is

        final int position = intent.getIntExtra(POSITION_PARAM, -1);
        if (!hasCallbacks(position)) { return; } // callbacks got cancelled
        if (itemList.size() <= position) { return; } // asking thumbnails beyond the end of the list
        final Domain.Item item = itemList.get(position);

        publishItem(peekCallbacks(position), item);

        final FileBuilder file = item.getFile();

        if (file.exists()) {
            try {
                // Thumbnails are 90dp wide. Convert that to the pixel equivalent:
                final float smallPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THUMB_SIZE, getResources().getDisplayMetrics());

                Bitmap bitmap = BitmapFactory.decodeStream(file.read());

                if (bitmap == null) {
                    Log.w(TAG, "decodeStream returned null for "+ file);
                    return;
                }
                // scale the *shortest* dimension to 'smallPx' so (after cropping) the image fills the whole thumbnail
                float ratio = smallPx / Math.min(bitmap.getWidth(), bitmap.getHeight());

                bitmap = Bitmap.createScaledBitmap(bitmap,
                        (int) (bitmap.getWidth() * ratio),
                        (int) (bitmap.getHeight() * ratio), false);
                publishBitmap(getCallbacks(position), bitmap);

                cacheThumb(position, item, bitmap);
            } catch (FileNotFoundException fnf) {
                Log.e(TAG, "Could not load file but exists returns true", fnf);
            }
        } else {
            Log.i(TAG, "File does not exist: "+ file);

            // TODO allow actually setting status report..
            CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, new SettableStatusListener<>(), domain);
            if (server == null) { // offline
                Log.i(TAG, "Thumbnail download disabled because offline");
                publishBitmap(getCallbacks(position), null);
            } else { // online
                server.downloadThumb(item.serverId, THUMB_SIZE, new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(response, 0, response.length);
                        publishBitmap(getCallbacks(position), bitmap);
                        cacheThumb(position, item, bitmap);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Failed downloading thumbnail: "+ error);
                        publishBitmap(getCallbacks(position), null);
                    }
                });
            }
        }
    }

    private void cacheThumb(int position, Domain.Item item, Bitmap bitmap) {
        memoryCache.put(position, new BitmapWithItem(item, bitmap));
    }
}
