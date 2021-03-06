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
// TODO [MAJOR] Delete files from (thumbnail) cache as well
public class FreeSpaceMaker {
    private static final String TAG = "CloudSpill.FSM";
    private static final int MEGA = 1048576;
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
        this.items = domain.selectItems().orderAsc(Domain.ItemSchema.LATEST_ACCESS).list();
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
        Log.i(TAG, "Starting batch: creating folder list");
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
        boolean needLog = true;
        while (getMissingSpace() > 0 && index < items.size()) {
            //Log.d(TAG, "Index "+ index +"/"+ items.size());
            reportStatus(needLog);
            needLog = false;
            Domain.Item item = items.get(index);
            FileBuilder fb = item.getFile(); // TODO fb may be null?
            // Log.d(TAG, fb.toString());
            if (fb.getName().contains("IMG_20181229_123935")) {
                Log.d(TAG, "Processing "+ fb);
            }
            if (getMissingSpace(fb) > 0) { // only delete file if its filesystem needs space
                if (fb.getName().contains("IMG_20181229_123935")) {
                    Log.d(TAG, "FS needs space: "+ fb);
                }
                if (fb.exists()) {
                    needLog = true;
                    Log.d(TAG, "Attempting to delete "+ fb);
                    long size = fb.length();
                    if (fb.delete()) {
                        Log.d(TAG, "Deleted " + fb + " to save " + size + " bytes. Need "+ getMissingSpace(fb) +" more.");
                        this.deletedFiles++;
                    } else {
                        Log.e(TAG, "Failed deleting " + fb +". canWrite:"+ fb.canWrite() +". canRead:"+ fb.canRead());
                        status.updateMessage(StatusReport.Severity.ERROR, "Failed deleting some files");
                    }
                } else {
                    if (fb.getName().contains("IMG_20181229_123935")) {
                        Log.d(TAG, "File does not exist: "+ fb);
                    }
                }
            }
            index++;
        }
        logSpace();
        Log.i(TAG, "Batch complete at index "+ index +" of "+ items.size());
        if (getMissingSpace() > 0) {
            status.updateMessage(StatusReport.Severity.ERROR, "Could not free enough space: "+ getMissingSpace() +" bytes over");
        }
    }

    private void addFileSystem(FileBuilder folder) {
        File f = folder.getFileEquivalent();
        // temporay hack for FS that's completely full!
        while (f != null && f.getUsableSpace() == 0 && !f.toString().equals("/storage/emulated")) { f = f.getParentFile(); }
        if (f != null) {
            this.filesystems.add(f);
        }
    }
    long timer=0;
    private void reportStatus(boolean needLog) {
        long now = System.currentTimeMillis();
        long missingNow = getMissingSpace();
        if (missingNow == 0) {
            Log.d(TAG, "Status: goal reached ("+ (now - timer) +"ms)");
            status.updatePercent(100);
        } else {
            if (needLog) {
                Log.d(TAG, "Status: missing " + missingNow + " from initial " + missingBytes + " (" + (now - timer) + "ms)");
            }
            status.updateMessage(StatusReport.Severity.INFO, "Deleting files ("+ (missingNow/MEGA) +"M over quota)");
            if (missingNow > missingBytes) {
                status.updatePercent(0);
            } else if (missingBytes > 0) {
                status.updatePercent((int)((missingBytes - missingNow) * 100 / missingBytes));
            }
        }
        timer = now;
    }

    private void logSpace() {
        StringBuilder logMessage = new StringBuilder("Missing space report: ");
        for (File fs : filesystems) {
            logMessage.append(fs).append(": ").append("have ").append(fs.getUsableSpace()).append(", need ").append(getMissingSpace(fs)).append(" more. ");
        }
        Log.i(TAG, logMessage.toString());
    }
}
