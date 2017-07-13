package org.gamboni.cloudspill;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

/**
 * @author tendays
 */
public class CloudSpillIntentService extends IntentService {

    private static final String TAG = "CloudSpill.Worker";

    private static DirectoryScanner.StatusReport listener;

    private Domain domain;

    public CloudSpillIntentService() {
        super(CloudSpillIntentService.class.getName());
        this.domain = new Domain(this);
    }

    public static void setListener(DirectoryScanner.StatusReport listener) {
        CloudSpillIntentService.listener = listener;
    }

    public static void unsetListener(DirectoryScanner.StatusReport listener) {
        if (CloudSpillIntentService.listener == listener) {
            CloudSpillIntentService.listener = null;
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        CloudSpillServerProxy server = new CloudSpillServerProxy(CloudSpillIntentService.this);
        FreeSpaceMaker fsm = new FreeSpaceMaker(CloudSpillIntentService.this, domain);
                /* Highest priority: free some space so user may take more pictures */
        fsm.run();

                /* Second highest: upload pictures so they are backed up and available to other users */
        final DirectoryScanner ds = new DirectoryScanner(CloudSpillIntentService.this, domain, server,
                new DirectoryScanner.StatusReport() {
                    @Override
                    public void updatePercent(final int percent) {
                        DirectoryScanner.StatusReport delegate = listener;
                        if (delegate != null) {
                            delegate.updatePercent(percent);
                        }
                    }
                });
        ds.run();
        ds.waitForCompletion();
                /* TODO Finally: download pictures from other users. */

        domain.close();
    }
}
