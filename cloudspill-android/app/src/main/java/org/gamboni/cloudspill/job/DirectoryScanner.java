package org.gamboni.cloudspill.job;

import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.file.FileTypeChecker;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ConnectivityTestRequest;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by tendays on 24.06.17.
 */

public class DirectoryScanner {
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
        Log.d(TAG, "Starting run with queue "+ queue);
        for (Domain.Folder folder : domain.selectFolders()) {
            scan(folder, folder.getFile().target);
        }
    }

    public void close() {
    }

    private void scan(Domain.Folder root, File folder) {
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
        if (!folder.canExecute()) {
            Log.e(TAG, "Folder is not executable: "+ folder);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            Log.e(TAG, "Path is not a directory: "+ folder);
            return;
        }
        for (Domain.Item item : domain.selectItems(/*recentFirst*/true)) { // TODO select current user/folder only
            pathsInDb.add(item.path); // TODO support single attribute selects
        }
        Log.d(TAG, "Found "+ pathsInDb.size() +" items in database");

        int percentage = 0;
        int processed = 0;
        listener.updatePercent(percentage);

        for (File file : files) {
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

    private byte[] loadFile(File f, int length) {
        byte[] result = new byte[length];

        FileInputStream stream = null;
        try {
            stream = new FileInputStream(f);
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

    private void addFile(Domain.Folder root, final File file) {
        final String path = root.getFile().getRelativePath(file);
        if (pathsInDb.remove(path)) {
            Log.d(TAG, path +" already exists in DB");
            return;
        }
        Log.d(TAG, "Queuing...");
        queue(file.getPath());
        Log.d(TAG, "Loading file...");

        final String folder = root.name;
        byte[] body = loadFile(file, (int)file.length());

        server.upload(folder, path, body, new Response.Listener<Long>() {
            @Override
            public void onResponse(Long response) {
                Log.d(TAG, "Received new id "+ response);

                Domain.Item i = domain.new Item();
                i.folder = folder;

                i.latestAccess = new Date(file.lastModified());
                i.user = SettingsActivity.getUser(context);
                i.path = path;

                long id = i.insert();

                Log.d(TAG, "Added Item with id "+ id);

                addedCount++;
                unqueue(file.getPath());

                listener.updateMessage(StatusReport.Severity.INFO, "Scanning "+ folder +": "+ addedCount +" files uploaded");
            }
        },
        new Response.ErrorListener() {
            public void onErrorResponse(VolleyError e) {
                Log.e(TAG, "Failed uploading "+ path);
                unqueue(file.getPath());
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
