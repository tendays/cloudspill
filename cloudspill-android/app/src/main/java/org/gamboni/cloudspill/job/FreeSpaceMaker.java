package org.gamboni.cloudspill.job;

import android.content.Context;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
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
        long delta = minSpace - file.getUsableSpace();
        return (delta < 0) ? 0 : delta;
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
        this.filesystems = new HashSet<>();
        // Add filesystems of our folders
        for (Domain.Folder folder : domain.selectFolders()) {
            this.filesystems.add(folder.getFile().getFilesystemRoot());
        }
        // Add filesystems of other users' folders
        this.filesystems.add(SettingsActivity.getDownloadPath(context).getFilesystemRoot());

        this.missingBytes = getMissingSpace(); // for progress report

        logSpace();
        while (getMissingSpace() < minSpace && items.size() > index) {
            reportStatus();
            Domain.Item item = items.get(index);

            FileBuilder fb = item.getFile();
            if (getMissingSpace(fb) > 0) { // only delete file if its filesystem needs space
                File file = fb.target;
                if (file.exists()) {
                    long size = file.length();
                    if (file.delete()) {
                        Log.d(TAG, "Deleted " + file + " to save " + size + " bytes");
                        this.deletedFiles++;
                    } else {
                        Log.e(TAG, "Failed deleting " + file);
                        status.updateMessage(StatusReport.Severity.ERROR, "Failed deleting some files");
                    }
                }
            }
            index++;
        }
        logSpace();
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
