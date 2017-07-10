package org.gamboni.cloudspill;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;

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

    private int index = 0; // next file to delete

    public FreeSpaceMaker(Context context, Domain domain) {
        this.context = context;
        this.domain = domain;
        this.items = domain.selectItems(/*recentFirst*/false);
        this.minSpace = SettingsActivity.getMinSpaceBytes(context);
    }

    /** Delete files until there is enough space left, or until there is nothing left to delete.
     * This method may be called several times (after download, because there would be less free
     * space, and after upload because uploaded files may be deleted) */
    public void run() {
        Log.i(TAG, "Usable space available: "+ SettingsActivity.getFolderPath(context).getFreeSpace() +
                " bytes. Required space: "+ minSpace +" bytes.");
        /*while (SettingsActivity.getFolderPath(context).getFreeSpace() < minSpace &&
                 items.size() > index) {
            Domain.Item item = items.get(index);

            File file = item.getFile();
            if (file.exists()) {
                long size = file.length();
                if (file.delete()) {
                    Log.d(TAG, "Deleted "+ file +" to save "+ size +" bytes");
                } else {
                    Log.e(TAG, "Failed deleting "+ file);
                }
            }
            index++;
        }*/
    }
}
