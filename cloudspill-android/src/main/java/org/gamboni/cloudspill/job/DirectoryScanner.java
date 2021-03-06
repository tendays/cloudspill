package org.gamboni.cloudspill.job;

import android.content.Context;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.shared.util.FileTypeChecker;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by tendays on 24.06.17.
 */

public class DirectoryScanner {
    // not static because not threadsafe
    private final SimpleDateFormat exifTimestampFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
    private final List<SimpleDateFormat> mdTimestampFormats = Arrays.asList(
            new SimpleDateFormat("yyyy MM dd"),
            new SimpleDateFormat("yyyyMMdd'T'HHmmss.Z")); // 20200908T133539.000Z

    private final Context context;
    private final Domain domain;
    private final CloudSpillServerProxy server;
    private final Set<String> pathsInDb = new HashSet<>();
    private final StatusReport listener;

    private List<String> queue = new ArrayList<>();
//    private int queued = 0;
    private int addedCount = 0;

    private static final String TAG = "CloudSpill.DirScanner";

    public DirectoryScanner(Context context, Domain domain, CloudSpillServerProxy server, StatusReport listener) {
        this.context = context;
        this.domain = domain;
        this.server = server;
        this.listener = listener;
    }

    public void run() {
        // Trying to understand why videos have wrong timestamps
        final File mediaDateTestFile = new File("/storage/emulated/0/DCIM/Tnotecamera/VID_20180110_182900.mp4");
        if (mediaDateTestFile.exists()) {
            Log.d(TAG,
                    "getMediaDate test: " + this.getMediaDate(
                            new FileBuilder.FileBased(context, mediaDateTestFile),
                            null));
        }

        Log.d(TAG, "hotfix output: "+ domain.hotfix());

        Log.d(TAG, "Starting run with queue "+ queue);

        Domain.Query<Domain.Item> q = domain.selectItems()
            .eq(Domain.ItemSchema.USER, SettingsActivity.getUser(context));
        for (Domain.Item item : q.list()) { // TODO select current folder only
            pathsInDb.add(item.getPath()); // TODO support single attribute selects
        }
        q.close();
        Log.d(TAG, "Found "+ pathsInDb.size() +" items in database");

        for (Domain.Folder folder : domain.selectFolders()) {
            scan(folder, folder.getFile());
        }
    }

    public void close() {
    }

    private void scan(Domain.Folder root, FileBuilder folder) {
        listener.updateMessage(StatusReport.Severity.INFO, "Scanning "+ folder.getUri().getPath());
        Log.d(TAG, "Scanning "+ folder);
        if (!folder.exists()) {
            Log.e(TAG, "Folder does not exist: "+ folder);
            return;
        }
        if (!folder.canRead()) {
            Log.e(TAG, "Folder is not readable: "+ folder);
            return;
        }
        List<FileBuilder> files = folder.listFiles();
        if (files == null) {
            Log.e(TAG, "Path is not a directory: "+ folder);
            return;
        }

        int percentage = 0;
        int processed = 0;
        listener.updatePercent(percentage);

        for (FileBuilder file : files) {
            try {
                if (file.getName().startsWith(".")) {
                    Log.d(TAG, "Skipping " + file + " because it starts with a dot");
                    continue;
                }

                if (file.isDirectory()) {
                    scan(root, file);
                } else {

                    final String path = root.getFile().getRelativePath(file);
                    if (pathsInDb.remove(path)) {
                        // Log.d(TAG, path +" already exists in DB");
                        continue;
                    }
                    Log.d(TAG, file.toString());
                    byte[] preamble = loadFile(file, FileTypeChecker.PREAMBLE_LENGTH);
                    if (preamble == null) {
                        continue;
                    }
                    FileTypeChecker ftc = new FileTypeChecker(preamble);
                    final ItemType type = ftc.getType();
                    switch (type) {
                        case IMAGE:
                            addFile(root, file, path, type);
                            break;
                        case VIDEO:
                            // videos tend to be large, and 'addFile' requires them to fit in memory
                            streamFile(root, file, path, type);
                            break;
                        case UNKNOWN:
                            Log.d(TAG, "Not any recognised format: "+ bytesToHex(preamble));
                            break;
                    }
                }
            } finally {
                processed++;
                int newPercentage = processed * 100 / files.size();
                if (percentage != newPercentage) {
                    percentage = newPercentage;
                    listener.updatePercent(percentage);
                }
            }
        }

        Log.d(TAG, "Added "+ addedCount +" items in total");
    }

    // Source: https://stackoverflow.com/a/9855338
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private byte[] loadFile(FileBuilder f, int length) {
        byte[] result = new byte[length];

        InputStream stream = null;
        try {
            stream = context.getContentResolver().openInputStream(f.getUri());
            int off = 0;
            int readThisTime;
            while (off < length && (readThisTime = stream.read(result, off, length - off)) > 0) {
                off += readThisTime;
            }
            return result;

        } catch (IOException e) {
            Log.e(TAG, "File unreadable");
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private Date getMediaDate(FileBuilder file, byte[] content) {
        // TODO experimental re-implementation using mediaMetadataRetriever (which should support videos)
        final MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(this.context, file.getUri());

            String mdDate = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            if (mdDate != null) {
                for (SimpleDateFormat format : mdTimestampFormats) {
                    try {
                        return format.parse(mdDate);
                    } catch (ParseException e) {
                        // Ignore, and try next
                    }
                }
                Log.w(TAG, "Media date not in expected format: " + mdDate);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "MediaMetadataRetriever failed to analyse "+ file, e);
        } finally {
            metadataRetriever.release();
        }

            File f = file.getFileEquivalent();
            if (f != null) {
                try {
                    ExifInterface exif = new ExifInterface(f.getPath());
//                    context.getContentResolver().openInputStream(file.getUri()));
                    // unfortunately not available on API 22: new ByteArrayInputStream(content));
                    String exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME);
                    if (exifDate == null) {
                        Log.w(TAG, "No Exif datetime in " + file);
                    } else {
                        return exifTimestampFormat.parse(exifDate);
                    }
                } catch (IOException | ParseException e) {
                    Log.w(TAG, "Error reading EXIF data", e);
                    // ... then fallback to last modification date
                }
            }
        return file.lastModified();
    }

    private void addFile(Domain.Folder root, final FileBuilder file, final String path, final ItemType type) {
        Log.d(TAG, "Queuing...");
        queue(file.getUri().getPath());
        Log.d(TAG, "Loading file...");

        final String folder = root.get(Domain.FolderSchema.NAME);
        byte[] body = loadFile(file, (int)file.length());
        final Date date = getMediaDate(file, body);

        server.upload(folder, path, date, type, body, new Response.Listener<Long>() {
            @Override
            public void onResponse(Long response) {
                Log.d(TAG, "Received new id "+ response);

                Domain.Item i = domain.new Item();
                i.set(Domain.ItemSchema.SERVER_ID, response);
                i.set(Domain.ItemSchema.FOLDER, folder);
                i.set(Domain.ItemSchema.DATE, date);
                i.set(Domain.ItemSchema.LATEST_ACCESS, date);
                i.set(Domain.ItemSchema.USER, SettingsActivity.getUser(context));
                i.set(Domain.ItemSchema.PATH, path);
                i.set(Domain.ItemSchema.TYPE, type);

                long id = i.insert();

                Log.d(TAG, "Added Item with id "+ id);

                addedCount++;
                unqueue(file.getUri().getPath());

                listener.updateMessage(StatusReport.Severity.INFO, "Scanning "+ folder +": "+ addedCount +" files uploaded");
                listener.refresh();
            }
        },
        new Response.ErrorListener() {
            public void onErrorResponse(VolleyError e) {
                Log.e(TAG, "Failed uploading "+ path);
                unqueue(file.getUri().getPath());
            }
        }
        );
    }

    private InputStream getInputStream(FileBuilder file) {
        try {
            return context.getContentResolver().openInputStream(file.getUri());
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file unreadable :"+ file, e);
            return null;
        }
    }

    private void streamFile(Domain.Folder root, final FileBuilder file, final String path, final ItemType type) {
        Log.d(TAG, "Queuing...");
        queue(file.getUri().getPath());
        Log.d(TAG, "Loading file...");

        final String folder = root.get(Domain.FolderSchema.NAME);
        InputStream body = getInputStream(file);
        final Date date = getMediaDate(file, /*body (unused)*/null);

        server.upload(folder, path, date, type, body, file.length(), new Response.Listener<Long>() {
                    @Override
                    public void onResponse(Long response) {
                        Log.d(TAG, "Received new id "+ response);

                        Domain.Item i = domain.new Item();
                        i.set(Domain.ItemSchema.SERVER_ID, response);
                        i.set(Domain.ItemSchema.FOLDER, folder);
                        i.set(Domain.ItemSchema.DATE, date);
                        i.set(Domain.ItemSchema.LATEST_ACCESS, date);
                        i.set(Domain.ItemSchema.USER, SettingsActivity.getUser(context));
                        i.set(Domain.ItemSchema.PATH, path);
                        i.set(Domain.ItemSchema.TYPE, type);
                        long id = i.insert();

                        Log.d(TAG, "Added Item with id "+ id);

                        addedCount++;
                        unqueue(file.getUri().getPath());

                        listener.updateMessage(StatusReport.Severity.INFO, "Scanning "+ folder +": "+ addedCount +" files uploaded");
                    }
                },
                new Response.ErrorListener() {
                    public void onErrorResponse(VolleyError e) {
                        Log.e(TAG, "Failed uploading "+ path);
                        unqueue(file.getUri().getPath());
                    }
                }
        );
    }



    private void queue(String item) {
        waitForQueueSize(item, 2);
    }

    private synchronized void waitForQueueSize(String item, int threshold) {
        while (queue.size() > threshold) {
            try {
                Log.d(TAG, "Blocking "+ item +" because queue is full: "+ queue);
                wait();
            } catch (InterruptedException e) {}
        }
        if (item != null) {
            queue.add(item);
        }
    }

    private synchronized void unqueue(String item) {
        queue.remove(item);
        notify();
    }

    public void waitForCompletion() {
        waitForQueueSize(null, 0);
        listener.updatePercent(100);
        listener.updateMessage(StatusReport.Severity.INFO, "Upload complete");
    }
}
