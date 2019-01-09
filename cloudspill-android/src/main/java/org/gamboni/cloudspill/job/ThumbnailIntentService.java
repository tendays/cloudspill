package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.EvaluatedFilter;
import org.gamboni.cloudspill.domain.FilterSpecification;
import org.gamboni.cloudspill.domain.ItemType;
import org.gamboni.cloudspill.file.DiskLruCache;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.os.Environment.isExternalStorageRemovable;

/** This service is responsible for acquiring thumbnails for local or remote items and passing them to
 * callbacks in the form of {@link Bitmap} objects.
 *
 * @author tendays
 */

public class ThumbnailIntentService extends IntentService {

    public static final String POSITION_PARAM = "position";
    public static final String ITEM_ID_PARAM = "itemId";
    private static final String TAG = "CloudSpill.Thumbnails";
    private static final int THUMB_SIZE = 90;
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    /** This disk cache key stores the size of the data record in bytes. */
    private static final int DISK_CACHE_SIZE_KEY = 0;
    /** This disk cache key stores the binary image compressed data. */
    private static final int DISK_CACHE_DATA_KEY = 1;

    private Domain domain;

    private EvaluatedFilter evaluatedFilter = null;

    // Using a Set in case callbacks define equality, to avoid redundant invocations
    private static final Map<CallbackKey, Set<Callback>> callbacks = new HashMap<>();
    private static final Map<Callback, CallbackKey> callbackKeys = new HashMap<>();
    private static FilterSpecification currentFilter = FilterSpecification.defaultFilter();

    private static final LruCache<Integer, BitmapWithItem> memoryCache;

    // Source: https://developer.android.com/topic/performance/graphics/cache-bitmap.html
    private static DiskLruCache diskLruCache;

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

    /** Ensure disk cache is ready and return it (must be done on service thread).
     * If cache could not be created, returns null. */
    private @Nullable
    DiskLruCache getDiskCache() {
        if (diskLruCache == null) {

// Creates a unique subdirectory of the designated app cache directory. Tries to use external
// but if not mounted, falls back on internal storage.
            // Check if media is mounted or storage is built-in, if so, try and use external cache dir
            // otherwise use internal cache dir

            final File externalCacheDir = getExternalCacheDir();
            final String cachePath;
            if ((Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                    !isExternalStorageRemovable()) && externalCacheDir != null) {
                cachePath = externalCacheDir.getPath();
            } else {
                cachePath = this.getCacheDir().getPath();
            }

            File cacheDir = new File(cachePath + File.separator + "thumbs");

            try {
                diskLruCache = DiskLruCache.open(cacheDir, 0, 2, DISK_CACHE_SIZE);
            } catch (IOException e) {
                Log.e(TAG, "Failed initialising cache directory " + cacheDir, e);
            }
        }
        return diskLruCache;
    }

    private static class BitmapWithItem {
        final Domain.Item item;
        final Bitmap bitmap;

        BitmapWithItem(Domain.Item item, Bitmap bitmap) {
            this.item = item;
            this.bitmap = bitmap;
        }
    }

    public ThumbnailIntentService() {
        super(ThumbnailIntentService.class.getSimpleName());
    }

    public static void setFilter(FilterSpecification filter) {
        Log.i(TAG, "Filter changed to: "+ filter);
        currentFilter = filter;
        forceRefresh();
    }

    public static FilterSpecification getCurrentFilter() {
        return currentFilter;
    }

    public static void forceRefresh() {
        memoryCache.evictAll();
    }

    public interface Callback {
        void setItem(Domain.Item item);
        void setThumbnail(Bitmap bitmap);
        void setStatus(DownloadStatus status);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.domain = new Domain(this);

        runQuery();
    }

    private void runQuery() {
        this.evaluatedFilter = new EvaluatedFilter(domain, currentFilter);
    }

    @Override
    public void onDestroy() {
        if (evaluatedFilter != null) {
            evaluatedFilter.close();
            evaluatedFilter = null;
        }

        domain.close();
        domain = null;

        super.onDestroy();
    }

    private interface CallbackKey {}

    private static class PositionKey implements CallbackKey {
        public final int position;
        public PositionKey(int position) {
            this.position = position;
        }
        public int hashCode() {
            return position;
        }
        public boolean equals(Object o) {
            return (o instanceof PositionKey) && ((PositionKey)o).position == this.position;
        }
    }
    private static class IdKey implements CallbackKey {
        public final long id;
        public IdKey(long id) {
            this.id = id;
        }
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }
        public boolean equals(Object o) {
            return (o instanceof IdKey) && ((IdKey)o).id == this.id;
        }
    }

    private static CallbackKey positionKey(int position) {
        return new PositionKey(position);
    }

    private static CallbackKey idKey(long id) {
        return new IdKey(id);
    }

    private static void registerCallback(CallbackKey key, Callback callback) {
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
            CallbackKey key = callbackKeys.remove(callback);
            Set<Callback> set = callbacks.get(key);
            if (set != null) {
                set.remove(callback);
            }
        }
    }

    private static boolean hasCallbacks(CallbackKey key) {
        synchronized (callbacks) {
            Set<Callback> result = callbacks.get(key);
            return (result != null) && result.size() > 0;
        }
    }

    private static Set<Callback> peekCallbacks(CallbackKey key) {
        synchronized (callbacks) {
            Set<Callback> result = callbacks.get(key);
            Set<Callback> copy = new HashSet<>();
            if (result != null) {
                copy.addAll(result);
            }
            return copy;
        }
    }

    private static Set<Callback> getCallbacks(CallbackKey key) {
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

    private static void publishStatus(Set<Callback> callbacks, DownloadStatus status) {
        for (Callback callback : callbacks) {
            callback.setStatus(status);
        }
    }

    public static void loadThumbnailAtPosition(Context context, int position, Callback callback) {
        BitmapWithItem cached = memoryCache.get(position);

        if (cached != null) {
            Log.d(TAG, "Found "+ position +" in mem cache (serverId="+ cached.item.getServerId() +")");
            final Set<Callback> callbackSet = Collections.singleton(callback);
            publishItem(callbackSet, cached.item);
            publishBitmap(callbackSet, cached.bitmap);
            return;
        }

        Log.d(TAG, "Loading position "+ position);
        Intent intent = new Intent(context, ThumbnailIntentService.class);
        intent.putExtra(POSITION_PARAM, position);

        registerCallback(positionKey(position), callback);

        context.startService(intent);
    }

    public static void loadThumbnailForId(Context context, long id, Callback callback) {
        Log.d(TAG, "Loading item id "+ id);
        Intent intent = new Intent(context, ThumbnailIntentService.class);
        intent.putExtra(ITEM_ID_PARAM, id);

        registerCallback(idKey(id), callback);

        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) { return; } // Intent should not be null, but well there's nothing to do if it is
        if (this.evaluatedFilter.isStale(currentFilter)) {
            runQuery();
        }
        EvaluatedFilter myFilter = this.evaluatedFilter;
        final int position = intent.getIntExtra(POSITION_PARAM, -1);
        final long id = intent.getLongExtra(ITEM_ID_PARAM, -1);
        final CallbackKey key = (position == -1) ? idKey(id) : positionKey(position);
        if (!hasCallbacks(key)) {
            Log.d(TAG, "Task "+ key +" got cancelled");
            return;
        }
        if (position >= myFilter.size()) { // note: won't apply if position == -1
            Log.d(TAG, "Aborting task "+ key +": exceeds size "+ myFilter.size());
            return;
        }
        final Domain.Item item;
        if (position != -1) {
            item = myFilter.getByPosition(position);
        } else {
            item = myFilter.getById(id);
        }
        Log.d(TAG, "Item "+ item.getId() +" at "+ item.getPath() +" with server id "+ item.getServerId());
        final String diskCacheKey = String.valueOf(item.getServerId());

        publishItem(peekCallbacks(key), item);

        final DiskLruCache diskCache = getDiskCache();

        if (diskCache != null) {
            try {
                final DiskLruCache.Snapshot cacheEntry = diskCache.get(diskCacheKey);
                if (cacheEntry != null) {
                    Log.d(TAG, "Found disk cache entry for id "+ diskCacheKey);
                    // Inspired by https://stackoverflow.com/a/36519516
                    byte[] bytes = new byte[Integer.parseInt(cacheEntry.getString(DISK_CACHE_SIZE_KEY))];
                    final InputStream inputStream = cacheEntry.getInputStream(DISK_CACHE_DATA_KEY);
                    int off=0;
                    int len;
                    while ((len = inputStream.read(bytes, off, bytes.length - off)) > 0) {
                        off += len;
                    }
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, off);

                    publishBitmap(getCallbacks(key), bitmap);
                    cacheThumb(position, item, bitmap);
                    return;
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed reading item " + item.getServerId() + " from disk cache", e);
                // Behave as if missing from cache
            }
        }

        final FileBuilder file = item.getFile();

        if (file.exists()) {
            try {
                Log.d(TAG, "Creating thumbnail for local "+ item.getType().name().toLowerCase());
                if (item.getType() == ItemType.IMAGE) {
                    // Thumbnails are 90dp wide. Convert that to the pixel equivalent:
                    final float smallPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THUMB_SIZE, getResources().getDisplayMetrics());

                    // TODO set sample size to avoid OOM
                    Bitmap bitmap = BitmapFactory.decodeStream(file.read());

                    if (bitmap == null) {
                        Log.w(TAG, "decodeStream returned null for " + file);
                        return;
                    }
                    // scale the *shortest* dimension to 'smallPx' so (after cropping) the image fills the whole thumbnail
                    float ratio = smallPx / Math.min(bitmap.getWidth(), bitmap.getHeight());

                    bitmap = Bitmap.createScaledBitmap(bitmap,
                            (int) (bitmap.getWidth() * ratio),
                            (int) (bitmap.getHeight() * ratio), false);
                    publishBitmap(getCallbacks(key), bitmap);

                /* Don't bother caching thumbnail if full image exists on disk. */
                    cacheThumb(position, item, bitmap);
                } else if (item.getType() == ItemType.VIDEO) {
                    final File fileEquivalent = file.getFileEquivalent();
                    if (fileEquivalent != null) {
                        final Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(fileEquivalent.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
                        publishBitmap(getCallbacks(key), thumbnail);
                    }
                } else {
                    Log.w(TAG, item.getPath() +" has no specified type");
                }
            } catch (FileNotFoundException fnf) {
                Log.e(TAG, "Could not load file but exists returns true", fnf);
            }
        } else {
            Log.i(TAG, "File does not exist: "+ file);

            // TODO allow actually setting status report..
            CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, new SettableStatusListener<>(), domain);
            if (server == null) { // offline
                Log.i(TAG, "Thumbnail download disabled because offline");

                publishStatus(getCallbacks(key), DownloadStatus.OFFLINE);
            } else { // online
                publishStatus(peekCallbacks(key), DownloadStatus.DOWNLOADING);
                server.downloadThumb(item.getServerId(), THUMB_SIZE, new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(response, 0, response.length);
                        publishBitmap(getCallbacks(key), bitmap);
                        cacheThumb(position, item, bitmap);
                        if (diskCache != null) {
                            try {
                                final DiskLruCache.Editor cacheEditor = diskCache.edit(diskCacheKey);
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 5, bytes);
                                cacheEditor.set(DISK_CACHE_SIZE_KEY, String.valueOf(bytes.size()));
                                cacheEditor.newOutputStream(DISK_CACHE_DATA_KEY).write(bytes.toByteArray(), 0, bytes.size());
                                cacheEditor.commit();
                                Log.d(TAG, "Created disk cache entry for id "+ diskCacheKey);
                            } catch (IOException e) {
                                Log.e(TAG, "Failed writing to disk cache", e);
                            }
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Failed downloading thumbnail: "+ error);
                        publishStatus(getCallbacks(key), DownloadStatus.ERROR);
                    }
                });
            }
        }
    }

    private void cacheThumb(int position, Domain.Item item, Bitmap bitmap) {
        if (position != -1) {
            memoryCache.put(position, new BitmapWithItem(item, bitmap));
        }
    }
}
