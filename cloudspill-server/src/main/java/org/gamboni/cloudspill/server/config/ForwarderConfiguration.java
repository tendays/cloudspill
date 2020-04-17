package org.gamboni.cloudspill.server.config;

import com.google.common.base.Preconditions;

/**
 * @author tendays
 */
public class ForwarderConfiguration extends BackendConfiguration {

    public ForwarderConfiguration(String path) {
        super(path);
    }

    public String getRemoteServer() {
        return requireProperty("remoteServer");
    }

}
