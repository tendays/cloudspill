package org.gamboni.cloudspill.job;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** This class is responsible for deleting files when available space is running low.
 *
 * @author tendays
 */
public class FreeSpaceMaker {
    private static final String TAG = "CloudSpill.FSM";

    private final Context context;
    private final Domain domain;
    private final List<Domain.Item> items;
    private final long minSpace;
    private StatusReport status;
    private Set<File> filesystems;
    private int index = 0; // next file to delete
    private int deletedFiles = 0;
    private long missingBytes;

    public FreeSpaceMaker(Context context, Domain domain, StatusReport status) {
        this.context = context;
        this.domain = domain;
        this.items = domain.selectItems().orderAsc(Domain.Item._LATEST_ACCESS).list();
        this.minSpace = SettingsActivity.getMinSpaceBytes(context);
        this.status = status;
    }

    /** Get the missing space in bytes on the filesystem containing the given FileBuilder. */
    private long getMissingSpace(FileBuilder file) {
        return file.getMissingSpace(minSpace);
    }

    /** Get the missing space in bytes on the filesystem whose root is at the given file
     * (the call will return an incorrect value if the given file is not a filesystem rooot). */
    private long getMissingSpace(File file) {
        long delta = minSpace - file.getUsableSpace();
        return (delta < 0) ? 0 : delta;
    }

    private long getMissingSpace() {
        long total = 0;
        for (File fs : filesystems) {
            total += getMissingSpace(fs);
        }
        return total;
    }

    /** Delete files until there is enough space left, or until there is nothing left to delete.
     * This method may be called several times (after download, because there would be less free
     * space, and after upload because uploaded files may be deleted) */
    public void run() {
        // Various attempts at writing to SD card follow:

        /*Log.d(TAG, Environment.getExternalStorageDirectory() +" is instate "+ Environment.getExternalStorageState());
        Log.d(TAG, "Write permission available: "+ (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED));

        Log.i(TAG, "ExternalFilesDir is "+ context.getExternalFilesDir(null));

        try {
            FileWriter fw = new FileWriter("/storage/3563-3866/DCIM/Camera/test");
            fw.write("test");
            fw.flush();
            fw.close();
        } catch (IOException e) {
            Log.w(TAG, "Can't write test file", e);
        }

        Log.d(TAG, "context.deleteFile: "+ context.deleteFile("/storage/3563-3866/DCIM/Camera/20160130_225811.jpg"));
        */
        this.filesystems = new HashSet<>();
        try {
            // Add filesystems of our folders
            for (Domain.Folder folder : domain.selectFolders()) {
                addFileSystem(folder.getFile());
            }
        } catch (IllegalArgumentException badUri) {
            status.updateMessage(StatusReport.Severity.ERROR, "One of the folders has an invalid path: "+ badUri);
            Log.e(TAG, "Invalid folder URI", badUri);
        }

        try {
            // Add filesystems of other users' folders
            addFileSystem(SettingsActivity.getDownloadPath(context));
        } catch (IllegalArgumentException badUri) {
            status.updateMessage(StatusReport.Severity.ERROR, "Download folder has an invalid path: "+ badUri);
            Log.e(TAG, "Invalid download folder URI", badUri);
        }

        this.missingBytes = getMissingSpace(); // for progress report

        logSpace();
        while (getMissingSpace() > 0 && index < items.size()) {
            reportStatus();
            Domain.Item item = items.get(index);
            FileBuilder fb = item.getFile(); // TODO fb may be null?
            if (getMissingSpace(fb) > 0) { // only delete file if its filesystem needs space
                if (fb.exists()) {
                    long size = fb.length();
                    if (fb.delete()) {
                        Log.d(TAG, "Deleted " + fb + " to save " + size + " bytes. Need "+ getMissingSpace(fb) +" more.");
                        this.deletedFiles++;
                    } else {
                        Log.e(TAG, "Failed deleting " + fb +". canWrite:"+ fb.canWrite() +". canRead:"+ fb.canRead());
                        status.updateMessage(StatusReport.Severity.ERROR, "Failed deleting some files");
                    }
                }
            }
            index++;
        }
        logSpace();
    }

    private void addFileSystem(FileBuilder folder) {
        File f = folder.getFileEquivalent();
        if (f != null) {
            this.filesystems.add(f);
        }
    }

    private void reportStatus() {
        long missingNow = getMissingSpace();
        if (missingNow == 0) {
            status.updatePercent(100);
        } else {
            status.updateMessage(StatusReport.Severity.INFO, "Deleting files ("+ missingNow +" bytes over quota)");
            if (missingNow > missingBytes) {
                status.updatePercent(0);
            } else if (missingBytes > 0) {
                status.updatePercent((int)((missingBytes - missingNow) * 100 / missingBytes));
            }
        }
    }

    private void logSpace() {
        StringBuilder logMessage = new StringBuilder("Missing space: ");
        for (File fs : filesystems) {
            logMessage.append(fs).append(": ").append(getMissingSpace(fs)).append(" bytes. ");
        }
        Log.i(TAG, logMessage.toString());
    }
}
