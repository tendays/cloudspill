package org.gamboni.cloudspill.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import org.gamboni.cloudspill.file.FileBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author tendays
 */
public abstract class ImageLoader {
    private static final String TAG="CloudSpill.ImageLoader";

    public static Bitmap fromUri(Context context, Uri uri, int reqWidth, int reqHeight) throws IOException {
        final BitmapFactory.Options options = computeOptions(reqWidth, reqHeight, context, uri);
        return doDecode(options, context, uri);
    }

    public static Bitmap fromFile(FileBuilder file, int reqWidth, int reqHeight) throws IOException {
        File javaIoFile = file.getFileEquivalent();
        // First decode with inJustDecodeBounds = true to check dimensions
        final BitmapFactory.Options options = decodeBoundOptions();
        decodeFileBuilder(file, javaIoFile, options);

        calculateInSampleSize(options, reqWidth, reqHeight);

        final long before = beforeDecode("java.io.File is " + javaIoFile, options);
        Bitmap bitmap = decodeFileBuilder(file, javaIoFile, options);
        afterDecode(before, bitmap);
        return bitmap;
    }

    private static void afterDecode(long before, Bitmap bitmap) {
        long allocated = before - Runtime.getRuntime().freeMemory();
        Log.d(TAG, "Allocated about " + allocated / 1024 + "K for " + bitmap.getWidth() + "x" + bitmap.getHeight() + " image. " +
                "(about " + allocated / (bitmap.getWidth() * bitmap.getHeight()) + " bytes per pixel). " +
                "Remaining: " + (before - allocated) / 1024 + "K");
    }

    private static long beforeDecode(String info, BitmapFactory.Options options) {
        final int rawBytes = options.outWidth * options.outHeight * 4;
        long before = Runtime.getRuntime().freeMemory();
        if (rawBytes / options.inSampleSize / options.inSampleSize > before) {
            Runtime.getRuntime().gc();
            before = Runtime.getRuntime().freeMemory();
            Log.d(TAG, "Starting decodeFileBuilder of " + options.outWidth + "x" + options.outHeight + " image with " + before / 1024 + "K memory available. " + info);
            while (true) {
                final int estimatedAllocation = (rawBytes) / options.inSampleSize / options.inSampleSize;
                if (estimatedAllocation < before) {
                    break;
                }
                options.inSampleSize = options.inSampleSize * 2;
                Log.w(TAG, "Risk of OutOfMemoryError: " + estimatedAllocation / 1024 + "K > " + before / 1024 + "K. Doubling sample size to " + options.inSampleSize);
            }
        }
        return before;
    }

    @NonNull
    private static BitmapFactory.Options decodeBoundOptions() {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        return options;
    }

    private static Bitmap decodeFileBuilder(FileBuilder file, File javaIoFile, BitmapFactory.Options options) throws IOException {
        if (javaIoFile == null) {
            InputStream input = file.read();
            try {
                return BitmapFactory.decodeStream(input, null, options);
            } finally {
                input.close();
            }
        } else {
            return BitmapFactory.decodeFile(javaIoFile.getCanonicalPath(), options);
        }
    }

    private static Bitmap doDecode(BitmapFactory.Options options, Context context, Uri uri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        try {
            final long before = beforeDecode(" Uri:"+ uri, options);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) {
                Log.e(TAG, "Failed loading image");
                return null;
            }
            afterDecode(before, bitmap);
            return bitmap;
        } finally {
            input.close();
        }
    }

    @NonNull
    private static BitmapFactory.Options computeOptions(int reqWidth, int reqHeight, Context context, Uri uri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        // First decode with inJustDecodeBounds = true to check dimensions
        final BitmapFactory.Options options = decodeBoundOptions();
        try {
            BitmapFactory.decodeStream(input, null, options);
        } finally {
            input.close();
        }

        return calculateInSampleSize(options, reqWidth, reqHeight);
    }

    private static BitmapFactory.Options calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {

        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        // Calculate inSampleSize
        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps one of
            // height and width larger than the requested height and width (corresponds to fit-to-screen)
            while ((halfHeight / inSampleSize) > reqHeight
                    || (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        Log.i(TAG, "Required size: "+ reqWidth +"×"+ reqHeight +", Image size: "+ width +"×"+ height +
                ". Calculated sample size: "+ inSampleSize);

        options.inSampleSize = inSampleSize;

                // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        return options;
    }

    public static void runOnLoaderThread(Runnable runnable) {
        createLoaderThread().handler.post(runnable);
    }

    private static class LoaderThread extends Thread {
        public Handler handler;
        LoaderThread() {
            this.start();
        }

        public void run() {
            Looper.prepare();
            this.handler = new Handler();
            Looper.loop();
        }

        public void kill() {
            handler.getLooper().quitSafely();
        }
    }

    private static LoaderThread loaderThread;

    public static LoaderThread createLoaderThread() {
        LoaderThread lt = loaderThread;
        if (lt == null) {
            lt = new LoaderThread();
            loaderThread = lt;
        }
        return lt;
    }

    public static void killLoaderThread() {
        LoaderThread lt = loaderThread;
        loaderThread = null;
        if (lt != null) {
            lt.kill();
        }
    }
}
