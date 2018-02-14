package org.gamboni.cloudspill;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.Nullable;
import android.support.v17.leanback.system.Settings;
import android.util.Log;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.job.DirectoryScanner;
import org.gamboni.cloudspill.job.Downloader;
import org.gamboni.cloudspill.job.FreeSpaceMaker;
import org.gamboni.cloudspill.job.MediaDownloader;
import org.gamboni.cloudspill.message.SettableStatusListener;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.ui.SettingsActivity;

/** This class is reponsible for coordinating CloudSpill background jobs:
 * <ul>
 *     <li>{@link FreeSpaceMaker} to make free space,</li>
 *     <li>{@link DirectoryScanner} to upload local media to server</li>
 *     <li>{@link Downloader} to download media metadata from server</li>
 * </ul>
 *
 * Actual media download is handled by {@link org.gamboni.cloudspill.job.MediaDownloader}.
 *
 * @author tendays
 */
public class CloudSpillIntentService extends IntentService {

    private static final String PARAM_TRIGGER = "trigger";
    /** What triggerred execution of this service. */
    public enum Trigger {
        /** A scheduled trigger running in the background. */
        BACKGROUND,
        /** Automatic execution when app started or preference changed. */
        FOREGROUND,
        /** User explicitly requested execution. */
        MANUAL,
        /** User requested full database rebuild. */
        FULL
    }

    private static final String TAG = "CloudSpill.Worker";
    private static final SettableStatusListener<StatusReport> listener = new SettableStatusListener<>();

    public static Intent newIntent(Context context, Trigger trigger) {
        Intent intent = new Intent(context, CloudSpillIntentService.class);
        intent.putExtra(PARAM_TRIGGER, trigger.name());
        return intent;
    }

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
        SettingsActivity.PrefMobileUpload mobileUpload = SettingsActivity.getMobileUpload(this);
        Trigger trigger = Trigger.valueOf(intent.getStringExtra(PARAM_TRIGGER));
        Log.i(TAG, "Starting batch after "+ trigger +" Trigger");

        boolean full = (trigger == Trigger.FULL);

        FreeSpaceMaker fsm = new FreeSpaceMaker(CloudSpillIntentService.this, domain, listener);
        if (!full) {
            /* Highest priority: free some space so user may take more pictures */
            Log.i(TAG, "Running FreeSpaceMaker");
            fsm.run();
        }

        /* - the rest of the batch needs server connectivity - */

        Log.i(TAG, "Looking for server");
        CloudSpillServerProxy server = CloudSpillServerProxy.selectServer(this, listener, domain);
        // TODO use an exception instead
        if (server == null) {
            Log.i(TAG, "No connectivity available, aborting batch");
            return;
        }
        // if selectServer reused an old url, check it is still up
        if (!server.checkLink()) {
            CloudSpillServerProxy.invalidateServer();
            Log.i(TAG, "Lost connectivity, aborting batch");
            return;
        }

        if (!full) {
            if (mobileUpload.shouldRun(checkWifiOnAndConnected(), trigger)) {
                Log.i(TAG, "Running DirectoryScanner");
                /* Second highest: upload pictures so they are backed up and available to other users */
                final DirectoryScanner ds = new DirectoryScanner(this, domain, server, listener);
                ds.run();
                Log.i(TAG, "Waiting for DirectoryScanner to complete");
                ds.waitForCompletion();
            } else {
                Log.i(TAG, "DirectoryScanner disabled by mobile upload policy");
            }
        }

        /* Finally: download pictures from other users. */
        Log.i(TAG, "Running Downloader");
        Downloader dl = new Downloader(this, domain, fsm, server, listener);
        if (full) {
            dl.rebuildDb();
        } else {
            dl.run();
        }

        domain.close();

        Log.i(TAG, "Batch complete");


    }

    private SettingsActivity.ConnectionType checkWifiOnAndConnected() {
        // Source: https://stackoverflow.com/a/34904367
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        if (wifiMgr != null && wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            if( wifiInfo.getNetworkId() == -1 ){
                return SettingsActivity.ConnectionType.MOBILE; // Not connected to an access point
            }
            return SettingsActivity.ConnectionType.WIFI; // Connected to an access point
        }
        else {
            return SettingsActivity.ConnectionType.MOBILE; // Wi-Fi adapter is OFF
        }
    }
}
