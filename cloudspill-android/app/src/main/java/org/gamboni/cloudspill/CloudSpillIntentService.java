package org.gamboni.cloudspill;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.job.DirectoryScanner;
import org.gamboni.cloudspill.job.Downloader;
import org.gamboni.cloudspill.job.FreeSpaceMaker;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

/** This class is reponsible for coordinating CloudSpill background jobs.
 *
 * @author tendays
 */
public class CloudSpillIntentService extends IntentService {

    private static final String TAG = "CloudSpill.Worker";
    private static final SettableStatusListener listener = new SettableStatusListener();

    private Domain domain;

    public CloudSpillIntentService() {
        super(CloudSpillIntentService.class.getName());
        this.domain = new Domain(this);
    }

    public static void setListener(StatusReport listener) {
        CloudSpillIntentService.listener.set(listener);
    }

    public static void unsetListener(StatusReport listener) {
        CloudSpillIntentService.listener.unset(listener);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        CloudSpillServerProxy server = new CloudSpillServerProxy(CloudSpillIntentService.this, domain);
        FreeSpaceMaker fsm = new FreeSpaceMaker(CloudSpillIntentService.this, domain, listener);
                /* Highest priority: free some space so user may take more pictures */
        fsm.run();

                /* Second highest: upload pictures so they are backed up and available to other users */
        final DirectoryScanner ds = new DirectoryScanner(CloudSpillIntentService.this, domain, server, listener);
        ds.run();
        ds.waitForCompletion();
                /* Finally: download pictures from other users. */
        Downloader dl = new Downloader(this, domain, fsm, server, listener);
        dl.run();

        domain.close();
    }
}
