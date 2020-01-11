package org.gamboni.cloudspill.job;

import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.AbstractDomain;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ItemsSinceRequest;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.util.BitSet;
import java.util.List;

/** This component is responsible for downloading database entries created by other users.
 *
 * @author tendays
 */
public class Downloader {
    private static final String TAG = "CloudSpill.Download";

    private final Context context;
    private final Domain domain;
    private final FreeSpaceMaker fsm;
    private final CloudSpillServerProxy server;
    private final StatusReport listener;

    public Downloader(Context context, Domain domain, FreeSpaceMaker fsm, CloudSpillServerProxy server, StatusReport listener) {
        this.context = context;
        this.domain = domain;
        this.fsm = fsm;
        this.server = server;
        this.listener = listener;
    }

    private boolean responded = false;
    private ItemsSinceRequest.Result result = null;

    public void rebuildDb() {
        run(true);
    }

    public void run() {
        run(false);
    }

    private void run(boolean full) {
        ServerInfo lastServer = SettingsActivity.getLastServerVersion(context);
        ServerInfo currentServer = server.getServerInfo();

        final long timestamp;

        BitSet falseMeansDelete;
        if (full) {
            // database query to get the highest id
            try (AbstractDomain.CloseableList<Domain.Item> highestIdQuery = domain.selectItems().orderDesc(Domain.ItemSchema.SERVER_ID).list()) {
                falseMeansDelete = new BitSet(highestIdQuery.get(0).getServerId().intValue() + 1);
            }
            timestamp = 0;
        } else {
            falseMeansDelete = new BitSet(0);
            timestamp = currentServer.moreRecentThan(lastServer) ?
                    0 : SettingsActivity.getLatestUpdate(context);
        }

        listener.updateMessage(StatusReport.Severity.INFO, "Downloading new items @"+ timestamp);
        server.itemsSince(timestamp, new Response.Listener<ItemsSinceRequest.Result>() {
            @Override
            public void onResponse(ItemsSinceRequest.Result result) {
                publish(result);
            }
        },
                // TODO create standard error listener instead of re-implementing ErrorListener everywhere
        new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Downloading failed", error);
                listener.updateMessage(StatusReport.Severity.ERROR, "Downloading failed: "+ error);
                publish(null);
            }
        });

        if (!waitForResponse()) { return; }

        int loaded = 0;
        int created = 0;
        int slowDown = 10;

        for (Domain.Item item : result) {
            loaded++;
            if (falseMeansDelete.size() > item.getServerId()) {
                falseMeansDelete.set(item.getServerId().intValue());
            }
            List<Domain.Item> allExisting = domain.selectItemsByServerId(item.getServerId());
            if (allExisting.isEmpty()) {
                item.insert();
                created++;
            } else {
                Domain.Item existing = allExisting.get(0);
                existing.copyFrom(item);
                existing.update();
            }

            if (slowDown-- == 0) { // TODO move this into StatusReport implementation (with new Severity.PROGRESS)
                listener.updateMessage(StatusReport.Severity.INFO, "Downloading new items @" + timestamp + ". Created " + created + "/" + loaded);
                slowDown = 10;
            }
        }

        int deleteCount = 0;
        for (int serverIdToDelete=falseMeansDelete.nextClearBit(0);
             serverIdToDelete < falseMeansDelete.size();
             serverIdToDelete = falseMeansDelete.nextClearBit(serverIdToDelete+1)) {
            domain.selectItems().eq(Domain.ItemSchema.SERVER_ID, (long)serverIdToDelete).delete();
            deleteCount++;

            if (slowDown-- == 0) { // TODO move this into StatusReport implementation (with new Severity.PROGRESS)
                listener.updateMessage(StatusReport.Severity.INFO, "Deleting stale items. Deleted " + deleteCount + "/" + serverIdToDelete);
                slowDown = 10;
            }
        }
        SettingsActivity.setLatestUpdate(context, result.getLatestUpdate());
        SettingsActivity.setLastServerVersion(context, currentServer);
        listener.updateMessage(StatusReport.Severity.INFO, "Download complete. Created " + created + "/" + loaded +
                (deleteCount>0 ? ". Deleted "+ deleteCount : ""));
    }

    private synchronized void publish(ItemsSinceRequest.Result result) {
        this.result = result;
        this.responded = true;
        notify();
    }

    private synchronized boolean waitForResponse() {
        while (!responded) {
            try {
                wait();
            } catch (InterruptedException i) {
                return false;
            }
        }

        return result != null;
    }
}
