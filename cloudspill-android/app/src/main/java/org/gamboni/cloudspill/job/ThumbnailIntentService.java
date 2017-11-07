package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.util.TypedValue;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tendays
 */

public class ThumbnailIntentService extends IntentService {

    private static final String TAG = "CloudSpill.Thumbnails";

    public static final String URI_PARAM = "uri";

    public ThumbnailIntentService() {
        super(ThumbnailIntentService.class.getSimpleName());
    }

    public interface Callback {
        void setThumbnail(Bitmap bitmap);
    }

    // Using a Set in case callbacks define equality, to avoid redundant invocations
    private static final Map<String, Set<Callback>> callbacks = new HashMap<>();

    private static String key(Uri uri) {
        return uri.toString();
    }

    private static void registerCallback(Uri uri, Callback callback) {
        String key = key(uri);
        synchronized (callbacks) {
            Set<Callback> set = callbacks.get(key);
            if (set == null) {
                set = new HashSet<>();
                callbacks.put(key, set);
            }
            set.add(callback);
        }
    }

    private static Set<Callback> getCallbacks(Uri uri) {
        String key = key(uri);
        synchronized (callbacks) {
            Set<Callback> result = callbacks.remove(key);
            return (result == null) ? Collections.<Callback>emptySet() : result;
        }
    }

    private static void invokeCallbacks(Uri uri, Bitmap bitmap) {
        for (Callback callback : getCallbacks(uri)) {
            callback.setThumbnail(bitmap);
        }
    }

    public static void loadThumbnail(Context context, FileBuilder file, Callback callback) {
        Log.d(TAG, "Loading "+ file);
        Intent intent = new Intent(context, ThumbnailIntentService.class);
        final Uri uri = file.getUri();
        intent.putExtra(URI_PARAM, uri);

        registerCallback(uri, callback);

        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) { return; } // Intent should not be null, but well there's nothing to do if it is
        final Uri uri = intent.<Uri>getParcelableExtra(URI_PARAM);

        final FileBuilder target;
        String FILE_URI = "file://";
        if (uri.toString().startsWith(FILE_URI)) {
            target = new FileBuilder.FileBased(this, new File(uri.toString().substring(FILE_URI.length())));
        } else {
            target = new FileBuilder.Found(this, DocumentFile.fromSingleUri(this, uri));
        }

        if (target.exists()) {
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
                Bitmap bitmap = BitmapFactory.decodeStream(target.read());

                //Bitmap bitmap = BitmapFactory.decodeFile(file.getFileEquivalent().getPath());

                if (bitmap == null) {
                    Log.w(TAG, "decodeStream returned null for "+ uri);
                    return;
                }
                // scale the *shortest* dimension to 'smallPx' so (after cropping) the image fills the whole thumbnail
                float ratio = smallPx / Math.min(bitmap.getWidth(), bitmap.getHeight());

                bitmap = Bitmap.createScaledBitmap(bitmap,
                        (int) (bitmap.getWidth() * ratio),
                        (int) (bitmap.getHeight() * ratio), false);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 10, baos); //What image format and level of compression to use.
                invokeCallbacks(uri, bitmap);
            } catch (FileNotFoundException fnf) {
                Log.e(TAG, "Could not load file but exists returns true", fnf);
            }
        } else {
            Log.i(TAG, "File does not exist: "+ uri);
            invokeCallbacks(uri, null);
        }
    }
}
