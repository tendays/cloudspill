package org.gamboni.cloudspill;

import android.content.Context;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
import java.util.List;

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

    private int index = 0; // next file to delete
    private int deletedFiles = 0;
    private final long missingBytes;

    public FreeSpaceMaker(Context context, Domain domain, StatusReport status) {
        this.context = context;
        this.domain = domain;
        this.items = domain.selectItems(/*recentFirst*/false);
        this.minSpace = SettingsActivity.getMinSpaceBytes(context);
        this.missingBytes = minSpace - getFreeSpace();
        this.status = status;
    }

    private long getFreeSpace() {
        return SettingsActivity.getFolderPath(context).getFreeSpace();
    }

    /** Delete files until there is enough space left, or until there is nothing left to delete.
     * This method may be called several times (after download, because there would be less free
     * space, and after upload because uploaded files may be deleted) */
    public void run() {
        logSpace();
        while (getFreeSpace() < minSpace && items.size() > index) {
            reportStatus();
            Domain.Item item = items.get(index);

            File file = item.getFile();
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
            index++;
        }
        logSpace();
    }

    private long reportStatus() {
        long freeSpace = getFreeSpace();
        if (freeSpace >= minSpace) {
            status.updatePercent(100);
        } else {
            long missingNow = minSpace - freeSpace;
            status.updateMessage(StatusReport.Severity.INFO, "Deleting files ("+ missingNow +" bytes over quota)");
            if (missingNow > missingBytes) {
                status.updatePercent(0);
            } else if (missingBytes > 0) {
                status.updatePercent((int)((missingBytes - missingNow) * 100 / missingBytes));
            }
        }
        return freeSpace;
    }

    private void logSpace() {
        long freeSpace = reportStatus();
        Log.i(TAG, "Usable space available: "+ freeSpace +
                " bytes. Required space: "+ minSpace +" bytes.");
    }
}
