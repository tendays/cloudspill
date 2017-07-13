package org.gamboni.cloudspill;

import android.content.Context;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;

/** This component is responsible for downloading database entries created by other users.
 *
 * @author tendays
 */
public class Downloader {
    private final Context context;
    private final Domain domain;
    private final FreeSpaceMaker fsm;
    private final CloudSpillServerProxy server;

    public Downloader(Context context, Domain domain, FreeSpaceMaker fsm, CloudSpillServerProxy server) {
        this.context = context;
        this.domain = domain;
        this.fsm = fsm;
        this.server = server;
    }

    public void run() {
        for (Domain.Item item : server.itemsSince(domain.getHighestId())) {

        }
    }
}
