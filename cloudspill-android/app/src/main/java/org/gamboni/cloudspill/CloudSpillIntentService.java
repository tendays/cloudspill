package org.gamboni.cloudspill;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.job.DirectoryScanner;
import org.gamboni.cloudspill.job.Downloader;
import org.gamboni.cloudspill.job.FreeSpaceMaker;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ConnectivityTestRequest;

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
        FreeSpaceMaker fsm = new FreeSpaceMaker(CloudSpillIntentService.this, domain, listener);
                /* Highest priority: free some space so user may take more pictures */
        fsm.run();

        /* - the rest of the batch needs server connectivity - */

        CloudSpillServerProxy server = null;
        for (final Domain.Server serverEntity : domain.selectServers()) {
            CloudSpillServerProxy testServer = new CloudSpillServerProxy(CloudSpillIntentService.this, domain, serverEntity.url);
            listener.updateMessage(StatusReport.Severity.INFO, "Connecting to "+ serverEntity.name);
            // First verify we are online
            if (testServer.checkLink()) {
                Log.i(TAG, "Server is up");
                listener.updateMessage(StatusReport.Severity.INFO, "Connected to "+ serverEntity.name);
                server = testServer;
                break;
            } else {
                Log.i(TAG, "No connection to server "+ serverEntity.name +" at "+ serverEntity.url);
            }
        }

        if (server == null) {
            listener.updateMessage(StatusReport.Severity.ERROR, "No connection to server");
            return;
        }

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
