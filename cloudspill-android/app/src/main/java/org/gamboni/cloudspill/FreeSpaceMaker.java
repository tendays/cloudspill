package org.gamboni.cloudspill;

import android.content.Context;
import android.preference.PreferenceManager;

import org.gamboni.cloudspill.domain.Domain;

import java.io.File;
import java.util.List;

/** This class is responsible for deleting files when available space is running low.
 *
 * @author tendays
 */
public class FreeSpaceMaker {
    private final Context context;
    private final Domain domain;
    private final List<Domain.Item> items;

    private int index = 0; // next file to delete

    public FreeSpaceMaker(Context context, Domain domain) {
        this.context = context;
        this.domain = domain;
        this.items = domain.selectItems(/*recentFirst*/false);
    }

    public void run() {
        while (SettingsActivity.getFolderPath(context).getFreeSpace() <
                SettingsActivity.getMinSpaceBytes(context) && items.size() > index) {
            Domain.Item item = items.get(index);

            File file = item.getFile();
            if (file.exists()) {
                file.delete();
            }
            index++;
        }
    }
}
