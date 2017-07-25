package org.gamboni.cloudspill.job;

import android.content.Context;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.ServerInfo;
import org.gamboni.cloudspill.message.StatusReport;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.ui.SettingsActivity;

/** This component is responsible for downloading database entries created by other users.
 *
 * @author tendays
 */
public class Downloader {
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

    boolean responded = false;
    Iterable<Domain.Item> items = null;

    public void run() {
        ServerInfo lastServer = SettingsActivity.getLastServerVersion(context);
        ServerInfo currentServer = server.getServerInfo();

        final long firstId = currentServer.moreRecentThan(lastServer) ?
                        0 : SettingsActivity.getHighestId(context);

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
            if (domain.selectItemsByServerId(item.serverId).isEmpty()) {
                item.insert();
                created++;
            }
            highestId = item.serverId;

            if (slowDown-- == 0) { // TODO move this into StatusReport implementation (with new Severity.PROGRESS)
                listener.updateMessage(StatusReport.Severity.INFO, "Downloading new items @" + firstId + ". Created " + created + "/" + loaded);
                slowDown = 10;
            }
        }
        SettingsActivity.setHighestId(context, highestId);
        listener.updateMessage(StatusReport.Severity.INFO, "Download complete. Created " + created + "/" + loaded);
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
