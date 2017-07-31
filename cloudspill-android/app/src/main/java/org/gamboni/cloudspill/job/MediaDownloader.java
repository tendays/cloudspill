package org.gamboni.cloudspill.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

/** This IntentService is responsible for downloading pictures that spring into view in the ui.
 * @author tendays
 */
public class MediaDownloader extends IntentService {
    private static final String PARAM_SERVER_ID = "serverId";
    private static final String PARAM_FILE = "file";

    Domain domain = new Domain(this);

    public static void download(Context context, long serverId, FileBuilder file) {
        Intent intent = new Intent(context, MediaDownloader.class);
        intent.putExtra(MediaDownloader.PARAM_SERVER_ID, serverId);
        intent.putExtra(MediaDownloader.PARAM_FILE, file.target.getPath());
        context.startService(intent);
    }

    public MediaDownloader() {
        super(MediaDownloader.class.getName());
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        // TODO allow actually setting status report..
        CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, new SettableStatusListener(), domain);
        if (server == null) { return; } // offline

        // TODO implement
    }
}
