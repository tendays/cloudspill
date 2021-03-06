package org.gamboni.cloudspill.shared.api;

/** Represents a version of the server-side CloudSpill data schema.
 *
 * @author tendays
 */
public class ServerInfo {
    private final boolean online;
    private final int version;
    private final CloudSpillApi api;

    private ServerInfo(boolean online, int version, String publicUrl) {
        this.online = online;
        this.version = version;
        this.api = new CloudSpillApi(publicUrl);
    }

    public static ServerInfo online(int version, String publicUrl) {
        return new ServerInfo(true, version, publicUrl);
    }

    public static ServerInfo offline(String url) {
        return new ServerInfo(false, 0, url);
    }

    public boolean isOnline() {
        return online;
    }

    public int getVersion() {
        if (!this.online) {
            throw new IllegalStateException("Can't obtain version of offline server");
        }
        return this.version;
    }

    public CloudSpillApi getApi() {
        return api;
    }

    /** Whether this version is strictly more recent than the given one. */
    public boolean moreRecentThan(ServerInfo that) {
        return this.getVersion() > that.getVersion();
    }
}
