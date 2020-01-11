package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.util.Splitter;

import java.util.ArrayList;

/**
 * @author tendays
 */
public abstract class PingResponseHandler {

    protected abstract void warn(String message);

    public ServerInfo parse(String serverResponse) {
        final Splitter splitter = new Splitter(serverResponse, '\n');
        String preamble = splitter.getString();
        if (!preamble.equals(CloudSpillApi.PING_PREAMBLE)) {
            warn("Not connecting to server with unexpected preamble "+ preamble);
            return ServerInfo.offline(); // TODO LOG
        }
        Integer version = null;
        String url = null;
        for (String line : splitter.allRemainingTo(new ArrayList<String>())) {
            int colon = line.indexOf(":");
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (key.equals(CloudSpillApi.PING_DATA_VERSION)) {
                version = Integer.parseInt(value);
            } else if (key.equals(CloudSpillApi.PING_PUBLIC_URL)) {
                url = value;
            } // else: assume key provided by later version of the server - ignore
        }
        if (version == null || url == null) {
            warn("Not connecting to server not specifying version or url: " + version +", "+ url);
            return ServerInfo.offline();
        } else {
            return ServerInfo.online(version, url);
        }
    }
}
