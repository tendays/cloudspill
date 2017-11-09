package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.TypedValue;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author tendays
 */

public class ThumbnailIntentService extends IntentService {

    private static final String TAG = "CloudSpill.Thumbnails";

    public static final String POSITION_PARAM = "position";

    private Domain domain;

    public ThumbnailIntentService() {
        super(ThumbnailIntentService.class.getSimpleName());
        this.domain = new Domain(this);
    }

    public interface Callback {
        void setThumbnail(Bitmap bitmap);
    }

    // Using a Set in case callbacks define equality, to avoid redundant invocations
    private static final Map<String, Set<Callback>> callbacks = new HashMap<>();
    private static final Map<Callback, String> callbackKeys = new HashMap<>();

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

    private static Set<Callback> getCallbacks(int position) {
        String key = key(position);
        synchronized (callbacks) {
            Set<Callback> result = callbacks.remove(key);
            return (result == null) ? Collections.<Callback>emptySet() : result;
        }
    }

    private static void invokeCallbacks(int position, Bitmap bitmap) {
        for (Callback callback : getCallbacks(position)) {
            callback.setThumbnail(bitmap);
        }
    }

    public static void loadThumbnail(Context context, int position, Callback callback) {

        Log.d(TAG, "Loading "+ position);//file);
        Intent intent = new Intent(context, ThumbnailIntentService.class);
        intent.putExtra(POSITION_PARAM, position);

        registerCallback(position, callback);

        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) { return; } // Intent should not be null, but well there's nothing to do if it is

        final AbstractDomain.Query<Domain.Item> itemQuery = domain.selectItems();
        final List<Domain.Item> itemList = itemQuery.orderDesc(Domain.Item._DATE).list();
        final int position = intent.getIntExtra(POSITION_PARAM, -1);
        if (itemList.size() <= position) { return; }
        final FileBuilder file = itemList.get(position).getFile();
        itemQuery.close();

        if (file.exists()) {
            // NOTE: this prints "it's a file" Log.d(TAG, DocumentFile.fromSingleUri(this, uri).isFile() ? "service: it's a file" : "service: it's not a file");
            try {
                // TODO I could not find documentation for that Thumbnails class
                //MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), itemList.get(position),
                //        MediaStore.Images.Thumbnails.MINI_KIND, null);

                // This works, but seems to keep the entire image in memory: imageView.setImageURI(file.getUri());

                    /* from https://stackoverflow.com/questions/13653526/how-to-find-origid-for-getthumbnail-when-taking-a-picture-with-camera-takepictur */

                // Thumbnails are 90dp wide. Convert that to the pixel equivalent:
                final float smallPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());

                // NOTE: this fails with "read failed: ESIDIR (Is a directory)
                Bitmap bitmap = BitmapFactory.decodeStream(file.read());

                //Bitmap bitmap = BitmapFactory.decodeFile(file.getFileEquivalent().getPath());

                if (bitmap == null) {
                    Log.w(TAG, "decodeStream returned null for "+ file);
                    return;
                }
                // scale the *shortest* dimension to 'smallPx' so (after cropping) the image fills the whole thumbnail
                float ratio = smallPx / Math.min(bitmap.getWidth(), bitmap.getHeight());

                bitmap = Bitmap.createScaledBitmap(bitmap,
                        (int) (bitmap.getWidth() * ratio),
                        (int) (bitmap.getHeight() * ratio), false);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 10, baos); //What image format and level of compression to use.
                invokeCallbacks(position, bitmap);
            } catch (FileNotFoundException fnf) {
                Log.e(TAG, "Could not load file but exists returns true", fnf);
            }
        } else {
            Log.i(TAG, "File does not exist: "+ file);
            invokeCallbacks(position, null);
        }
    }
}
