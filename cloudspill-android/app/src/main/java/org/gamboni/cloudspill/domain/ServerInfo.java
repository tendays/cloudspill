package org.gamboni.cloudspill.domain;

/** Represents a version of the server-side CloudSpill data schema.
 *
 * @author tendays
 */
public class ServerInfo {
    private final boolean online;
    private final int version;

    private ServerInfo(boolean online, int version) {
        this.online = online;
        this.version = version;
    }

    public static ServerInfo online(int version) {
        return new ServerInfo(true, version);
    }

    public static ServerInfo offline() {
        return new ServerInfo(false, 0);
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

    /** Whether this version is strictly more recent than the given one. */
    public boolean moreRecentThan(ServerInfo that) {
        return this.getVersion() > that.getVersion();
    }
}
