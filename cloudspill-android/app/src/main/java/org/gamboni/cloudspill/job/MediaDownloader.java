package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

/** This IntentService is responsible for downloading pictures that spring into view in the ui.
 * @author tendays
 */
public class MediaDownloader extends IntentService {
    public MediaDownloader() {
        super(MediaDownloader.class.getName());
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }
}
