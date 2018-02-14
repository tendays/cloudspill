package org.gamboni.cloudspill.job;

import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
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
    private Iterable<Domain.Item> items = null;

    public void rebuildDb() {
        run(true);
    }

    public void run() {
        run(false);
    }

    private void run(boolean full) {
        ServerInfo lastServer = SettingsActivity.getLastServerVersion(context);
        ServerInfo currentServer = server.getServerInfo();

        final long firstId;

        BitSet falseMeansDelete;
        if (full) {
            falseMeansDelete = new BitSet((int)SettingsActivity.getHighestId(context)+1);
            firstId = 0;
        } else {
            falseMeansDelete = new BitSet(0);
            firstId = currentServer.moreRecentThan(lastServer) ?
                    0 : SettingsActivity.getHighestId(context);
        }

        listener.updateMessage(StatusReport.Severity.INFO, "Downloading new items @"+ firstId);
        server.itemsSince(firstId, new Response.Listener<Iterable<Domain.Item>>() {
            @Override
            public void onResponse(Iterable<Domain.Item> items) {
                publish(items);
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
        long highestId = firstId;

        for (Domain.Item item : items) {
            loaded++;
            if (falseMeansDelete.size() > item.serverId) {
                falseMeansDelete.set((int) item.serverId);
            }
            List<Domain.Item> allExisting = domain.selectItemsByServerId(item.serverId);
            if (allExisting.isEmpty()) {
                item.insert();
                created++;
            } else {
                Domain.Item existing = allExisting.get(0);
                existing.copyFrom(item);
                existing.update();
            }
            highestId = item.serverId;

            if (slowDown-- == 0) { // TODO move this into StatusReport implementation (with new Severity.PROGRESS)
                listener.updateMessage(StatusReport.Severity.INFO, "Downloading new items @" + firstId + ". Created " + created + "/" + loaded);
                slowDown = 10;
            }
        }

        int deleteCount = 0;
        for (int serverIdToDelete=falseMeansDelete.nextClearBit(0);
             serverIdToDelete < falseMeansDelete.size();
             serverIdToDelete = falseMeansDelete.nextClearBit(serverIdToDelete+1)) {
            domain.new ItemQuery().eq(Domain.Item._SERVER_ID, serverIdToDelete).delete();
            deleteCount++;

            if (slowDown-- == 0) { // TODO move this into StatusReport implementation (with new Severity.PROGRESS)
                listener.updateMessage(StatusReport.Severity.INFO, "Deleting stale items. Deleted " + deleteCount + "/" + serverIdToDelete);
                slowDown = 10;
            }
        }
        SettingsActivity.setHighestId(context, highestId);
        SettingsActivity.setLastServerVersion(context, currentServer);
        listener.updateMessage(StatusReport.Severity.INFO, "Download complete. Created " + created + "/" + loaded +
                (deleteCount>0 ? ". Deleted "+ deleteCount : ""));
    }

    private synchronized void publish(Iterable<Domain.Item> items) {
        this.items = items;
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

        return items != null;
    }
}
