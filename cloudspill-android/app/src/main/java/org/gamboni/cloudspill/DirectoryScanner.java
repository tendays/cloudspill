package org.gamboni.cloudspill;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ConnectivityTestRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tendays on 24.06.17.
 */

public class DirectoryScanner {
    private final File root;
    private final Context context;
    private final Domain domain;
    private final CloudSpillServerProxy server;
    private final Set<String> pathsInDb = new HashSet<>();
    private final StatusReport report;

    private int queued = 0;
    private int addedCount = 0;

    private static final String TAG = "CloudSpill.DirScanner";

    public interface StatusReport {
        void updatePercent(int percent);
    }

    public DirectoryScanner(Context context, Domain domain, CloudSpillServerProxy server, File root, StatusReport report) {
        this.context = context;
        this.root = root;
        this.domain = domain;
        this.server = server;
        this.report = report;
    }

    public void run() {
        // First verify we are online
        queue();
        server.checkLink(new ConnectivityTestRequest.Listener() {
            @Override
            public void setResult(boolean online) {
                unqueue();
                if (online) {
                    scan(root);
                } else {
                    Log.i(TAG, "No connection to server, skipping upload");
                }
            }
        });
    }

    public void close() {
    }

    private void scan(File folder) {

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
        for (Domain.Item item : domain.selectItems()) { // TODO select current user/folder only
            pathsInDb.add(item.path); // TODO support single attribute selects
        }
        Log.d(TAG, "Found "+ pathsInDb.size() +" items in database");

        int percentage = 0;
        int processed = 0;
        report.updatePercent(percentage);

        for (File file : files) {
            Log.d(TAG, file.toString());

            byte[] preamble = loadFile(file, 4);
            if (preamble == null) { continue; }
            if (new FileTypeChecker(preamble).isJpeg()) {
                Log.d(TAG, "JPEG file");
                addFile(file);
            } else {
                Log.d(TAG, "Not a JPEG file");
            }

            processed++;
            int newPercentage = processed * 100 / files.length;
            if (percentage != newPercentage) {
                percentage = newPercentage;
                report.updatePercent(percentage);
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

    private void addFile(File file) {
        final String path = file.getPath().substring(root.getPath().length());
        if (pathsInDb.remove(path)) {
            Log.d(TAG, path +" already exists in DB");
            return;
        }

        queue();

        final String folder = SettingsActivity.getFolder(context);
        byte[] body = loadFile(file, (int)file.length());

        server.upload(folder, path, body, new Response.Listener<Long>() {
            @Override
            public void onResponse(Long response) {
                Log.d(TAG, "Received new id "+ response);

                Domain.Item i = domain.new Item();
                i.folder = folder;
                i.latestAccess = new Date(); // TODO should actually be the file creation date
                i.user = SettingsActivity.getUser(context);
                i.path = path;

                long id = i.insert();

                Log.d(TAG, "Added Item with id "+ id);

                addedCount++;
                unqueue();
            }
        },
        new Response.ErrorListener() {
            public void onErrorResponse(VolleyError e) {
                Log.e(TAG, "Failed uploading "+ path);
                unqueue();
            }
        }
        );
    }

    private void queue() {
        waitForQueueSize(2);
    }

    private synchronized void waitForQueueSize(int threshold) {
        while (queued > threshold) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
        queued++;
    }

    private synchronized void unqueue() {
        queued--;
        notify();
    }

    public void waitForCompletion() {
        waitForQueueSize(0);
    }
}
