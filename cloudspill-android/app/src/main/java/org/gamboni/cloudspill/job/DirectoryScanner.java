package org.gamboni.cloudspill.job;

import android.content.Context;
import android.media.ExifInterface;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.file.FileTypeChecker;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ConnectivityTestRequest;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        Log.d(TAG, "hotfix output: "+ domain.hotfix());

        Log.d(TAG, "Starting run with queue "+ queue);
        for (Domain.Folder folder : domain.selectFolders()) {
            scan(folder, folder.getFile().target);
        }
    }

    public void close() {
    }

    private void scan(Domain.Folder root, DocumentFile folder) {
        listener.updateMessage(StatusReport.Severity.INFO, "Scanning "+ folder);
        Log.d(TAG, "Scanning "+ folder);
        if (!folder.exists()) {
            Log.e(TAG, "Folder does not exist: "+ folder);
            return;
        }
        if (!folder.canRead()) {
            Log.e(TAG, "Folder is not readable: "+ folder);
            return;
        }

        DocumentFile[] files = folder.listFiles();
        if (files == null) {
            Log.e(TAG, "Path is not a directory: "+ folder);
            return;
        }
        AbstractDomain.Query<Domain.Item> q = domain.selectItems();
        for (Domain.Item item : q.list()) { // TODO select current user/folder only
            pathsInDb.add(item.path); // TODO support single attribute selects
        }
        q.close();
        Log.d(TAG, "Found "+ pathsInDb.size() +" items in database");

        int percentage = 0;
        int processed = 0;
        listener.updatePercent(percentage);

        for (DocumentFile file : files) {
            if (file.getName().startsWith(".")) {
                Log.d(TAG, "Skipping "+ file +" because it starts with a dot");
                continue;
            }
            Log.d(TAG, file.toString());
            if (file.isDirectory()) {
                scan(root, file);
            } else {
                byte[] preamble = loadFile(file, 4);
                if (preamble == null) {
                    continue;
                }
                if (new FileTypeChecker(preamble).isJpeg()) {
                    Log.d(TAG, "JPEG file");
                    addFile(root, file);
                } else { // TODO support videos as well
                    Log.d(TAG, "Not a JPEG file");
                }
            }

            processed++;
            int newPercentage = processed * 100 / files.length;
            if (percentage != newPercentage) {
                percentage = newPercentage;
                listener.updatePercent(percentage);
            }
        }

        Log.d(TAG, "Added "+ addedCount +" items in total");
    }

    private byte[] loadFile(DocumentFile f, int length) {
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
            // WTF: using Files to access external storage is "essentially deprecated", we have to use SAF instead.
            // However ExifInterface does not support either DocumentFile or InputStreams, so we have to use Files instead.

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
        return new Date(file.target.lastModified());
    }

    private void addFile(Domain.Folder root, final DocumentFile file) {
        final String path = root.getFile().getRelativePath(file);
        if (pathsInDb.remove(path)) {
            Log.d(TAG, path +" already exists in DB");
            return;
        }
        Log.d(TAG, "Queuing...");
        queue(file.getUri().getPath());
        Log.d(TAG, "Loading file...");

        final String folder = root.name;
        byte[] body = loadFile(file, (int)file.length());
        final Date date = getMediaDate(new FileBuilder(file), body);

        server.upload(folder, path, date, body, new Response.Listener<Long>() {
            @Override
            public void onResponse(Long response) {
                Log.d(TAG, "Received new id "+ response);

                Domain.Item i = domain.new Item();
                i.serverId = response;
                i.folder = folder;
                i.date = date;
                i.latestAccess = date;
                i.user = SettingsActivity.getUser(context);
                i.path = path;

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
